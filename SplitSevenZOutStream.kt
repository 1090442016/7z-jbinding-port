package com.killion.files.data.archive

import android.util.Log
import net.sf.sevenzipjbinding.ISeekableStream
import net.sf.sevenzipjbinding.IOutStream
import net.sf.sevenzipjbinding.SevenZipException
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * 7z split output stream (IOutStream adapter for the official LZMA SDK engine).
 *
 * The official engine writes data sequentially through [IOutStream] and seeks back to the volume head
 * to rewrite the signature header. This implementation slices the write by [splitLength] into
 * `name.7z.001`, `name.7z.002`, ... split files — same semantics as commons-compress's SplitSevenZChannel
 * (7z split = pure byte slicing; the extract side reads transparently via
 * MultiReadOnlySeekableByteChannel by concatenating files in order).
 *
 * Naming follows 7-Zip's convention: `name.7z.001`, `name.7z.002`, ... (last volume is `.7z.00N`,
 * no bare master file).
 *
 * Implementation notes:
 * - Working file = target path (`name.7z`); when a volume is full, rename it to `.7z.0NN` then create a
 *   new working file and continue writing.
 * - [seek] supports redirecting to an already-cut historical volume (engine's WriteFinish rewriting the
 *   start header scenario).
 * - [write] loops across volume boundaries; write/seek/setSize may be called by the engine on multiple
 *   threads, so all are synchronized.
 * - The passed ByteBuffer is a native-layer DirectByteBuffer, written straight out through FileChannel
 *   (zero copy).
 */
class SplitSevenZOutStream(
    private val basePath: File,
    private val splitLength: Long,
) : IOutStream, Closeable {

    companion object {
        private const val TAG = "Split7zOut"
    }

    init {
        require(splitLength > 0) { "splitLength must be positive" }
        require(!basePath.exists()) { "destination already exists: $basePath" }
        Log.d(TAG, "init: base=$basePath, splitLength=$splitLength")
    }

    private val parentDir = basePath.parentFile ?: throw IOException("no parent dir: $basePath")
    private val baseName = basePath.name

    /** Already-cut split volumes (in order, excluding the current working file). */
    private val partFiles = mutableListOf<File>()

    /** Current working file (target path, roll-renamed). */
    private val workFile = basePath

    /** File the current raf points to (working file or some historical volume). */
    private var rafTarget: File = workFile

    private var raf = RandomAccessFile(workFile, "rw")

    /** Global write position (accumulated across volumes). */
    private var globalPos = 0L

    private var open = true

    override fun write(data: ByteBuffer, len: Int): Int = synchronized(this) {
        if (!open) throw SevenZipException("stream closed")
        if (len <= 0) return 0
        val oldLimit = data.limit()
        if (len < oldLimit) data.limit(len)
        val start = data.position()
        try {
            while (data.hasRemaining()) {
                // Current volume full → roll to next (only triggered on sequential writes to the
                // working file; historical-volume rewrites never overflow).
                if (rafTarget == workFile && raf.filePointer >= splitLength) {
                    rollOver()
                }
                // Compute with Long to avoid toInt() overflow on very large splitLength.
                val n = minOf(splitLength - raf.filePointer, data.remaining().toLong()).toInt()
                val saved = data.limit()
                data.limit(data.position() + n)
                writeFully(data)
                data.limit(saved)
            }
        } finally {
            data.limit(oldLimit)
        }
        val written = data.position() - start
        globalPos += written
        Log.d(
            TAG,
            "write($written) -> globalPos=$globalPos, partFiles=${partFiles.size}, rafTarget=${rafTarget.name}, filePointer=${raf.filePointer}"
        )
        written
    }

    override fun seek(offset: Long, seekOrigin: Int): Long = synchronized(this) {
        if (!open) throw SevenZipException("stream closed")
        val newPos = when (seekOrigin) {
            ISeekableStream.SEEK_SET -> offset
            ISeekableStream.SEEK_CUR -> globalPos + offset
            ISeekableStream.SEEK_END -> globalPos + offset
            else -> throw SevenZipException("unknown seek origin: $seekOrigin")
        }
        if (newPos < 0) throw SevenZipException("negative seek position: $newPos")
        globalPos = newPos
        positionRaf(newPos)
        Log.d(
            TAG,
            "seek($offset, $seekOrigin) → globalPos=$globalPos, rafTarget=${rafTarget.name}, filePointer=${raf.filePointer}"
        )
        globalPos
    }

    /**
     * Truncate volumes by global size. The 7z engine compression path does NOT call this
     * (only seek+write), but it is implemented to honor the IOutStream contract: keep the
     * needed volumes, truncate the last one, and delete the extras.
     */
    override fun setSize(newSize: Long) {
        synchronized(this) {
            if (!open) throw SevenZipException("stream closed")
            if (newSize < 0) throw SevenZipException("negative size: $newSize")
            Log.d(TAG, "setSize($newSize): partFiles=${partFiles.size}, globalPos=$globalPos")
            val fullVols = newSize / splitLength
            val rem = newSize % splitLength
            val totalVols = if (rem > 0) fullVols + 1 else fullVols

            // Delete extra already-cut volumes.
            while (partFiles.size > totalVols) {
                partFiles.removeAt(partFiles.lastIndex).delete()
            }

            if (totalVols == 0L) {
                try {
                    raf.close()
                } catch (_: IOException) {
                }
                workFile.delete()
                raf = RandomAccessFile(workFile, "rw")
                rafTarget = workFile
                globalPos = 0
                return
            }

            val lastLen = if (rem > 0) rem else splitLength
            if (partFiles.size == totalVols.toInt() && rem > 0) {
                // Last volume is the working file: truncate to target length.
                if (rafTarget != workFile) switchRaf(workFile, raf.length())
                raf.setLength(lastLen)
            } else if (partFiles.size == totalVols.toInt() && rem == 0L) {
                // Data fills whole volumes exactly: working file should be empty, drop it.
                try {
                    raf.close()
                } catch (_: IOException) {
                }
                workFile.delete()
                raf = RandomAccessFile(workFile, "rw")
                rafTarget = workFile
            } else {
                // Last volume is a historical one: truncate it, drop the extra working file.
                val lastPart = partFiles[totalVols.toInt() - 1]
                switchRaf(lastPart, minOf(raf.length(), lastLen))
                raf.setLength(lastLen)
                if (workFile.exists()) workFile.delete()
            }
            globalPos = minOf(globalPos, newSize)
        }
    }

    override fun close() {
        synchronized(this) {
            if (!open) return
            open = false
            try {
                raf.close()
            } catch (_: IOException) {
            }
            // Rename the last working file to the final split volume.
            val finalIndex = partFiles.size + 1
            val finalFile = partFile(finalIndex)
            Log.d(
                TAG,
                "close: partFiles=${partFiles.map { it.name }}, finalIndex=$finalIndex, workFile.exists=${workFile.exists()}"
            )
            if (workFile.exists()) {
                if (finalFile.exists()) {
                    throw IOException("split file already exists: $finalFile")
                }
                if (!workFile.renameTo(finalFile)) {
                    throw IOException("cannot rename to final split file: $finalFile")
                }
            }
        }
    }

    // ──────────────── Internal ────────────────

    /** Number of cut volumes (after close, includes the final renamed working file = real volume count). */
    fun partCount(): Int = partFiles.size + if (workFile.exists()) 1 else 0

    private fun partFile(index: Int): File =
        File(parentDir, "$baseName.${String.format("%03d", index)}")

    private fun writeFully(buf: ByteBuffer) {
        while (buf.hasRemaining()) {
            raf.getChannel().write(buf)
        }
    }

    /** Map global position to (volume, in-volume offset), switching raf when needed. */
    private fun positionRaf(newPos: Long) {
        var remaining = newPos
        for (part in partFiles) {
            if (remaining < splitLength) {
                if (rafTarget != part || raf.filePointer != remaining) {
                    switchRaf(part, remaining)
                }
                return
            }
            remaining -= splitLength
        }
        if (rafTarget != workFile || raf.filePointer != remaining) {
            switchRaf(workFile, remaining)
        }
    }

    private fun switchRaf(file: File, offset: Long) {
        try {
            raf.close()
        } catch (_: IOException) {
        }
        raf = RandomAccessFile(file, "rw")
        raf.seek(offset)
        rafTarget = file
    }

    /** Rename the current working file to the next split volume and create a new working file. */
    private fun rollOver() {
        try {
            raf.close()
        } catch (_: IOException) {
        }
        val index = partFiles.size + 1
        val target = partFile(index)
        if (target.exists()) {
            throw IOException("split file already exists: $target")
        }
        if (!workFile.renameTo(target)) {
            throw IOException("cannot rename to split file: $target")
        }
        partFiles.add(target)
        raf = RandomAccessFile(workFile, "rw")
        rafTarget = workFile
    }
}
