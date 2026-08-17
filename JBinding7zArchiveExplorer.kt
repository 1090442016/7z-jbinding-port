package com.killion.files.data.archive

import android.util.Log
import com.killion.files.domainmodel.archive.ArchiveEntryFileInfo
import com.killion.files.domainmodel.archive.ArchiveType
import com.killion.files.utils.FileHelper
import net.sf.sevenzipjbinding.ICryptoGetTextPassword
import net.sf.sevenzipjbinding.IOutCreateCallback
import net.sf.sevenzipjbinding.IOutItem7z
import net.sf.sevenzipjbinding.IOutStream
import net.sf.sevenzipjbinding.ISequentialInStream
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.SevenZipException
import net.sf.sevenzipjbinding.impl.OutItemFactory
import net.sf.sevenzipjbinding.impl.RandomAccessFileOutStream
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.util.Date
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive

/**
 * 7z compression engine (official LZMA SDK 26.02 + JBinding bridge).
 *
 * Replaces the commons-compress 7z compression path (whose LZMA2OutputStream batch encoding blocks
 * write() for 3.5-9s and freezes progress). The official engine measured 4-5x faster with smooth progress.
 *
 * Only [compress] is implemented here; browse/extract is delegated to commons-compress's
 * [SevenZipArchiveExplorer] to keep the migration surface small.
 */
class JBinding7zArchiveExplorer(
    override val archiveFile: File,
    private val context: android.content.Context,
    private val delegate: SevenZipArchiveExplorer = SevenZipArchiveExplorer(archiveFile),
) : ArchiveExplorer {

    companion object {
        private const val TAG = "JBinding7z"
        private const val LEVEL = 6
        private const val PROGRESS_REPORT_INTERVAL_MS = 100L

        // ⚠️ LZMA2 multi-thread memory safety:
        // Each thread has its own dictionary buffer + encoder state. At level 6 (8 MiB dictionary),
        // each thread can use ~100 MB. Never call setThreadCount(0) (auto = CPU core count); an 8-core
        // device may eat 800 MB+ and trigger the OOM Killer. Limit threads to 2-4 by available memory.
        private fun computeThreadCount(context: android.content.Context): Int {
            return try {
                val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE)
                        as? android.app.ActivityManager
                val memInfo = android.app.ActivityManager.MemoryInfo()
                am?.getMemoryInfo(memInfo)
                val availMb = (memInfo.availMem ?: 0L) / (1024 * 1024)
                val totalMb = (memInfo.totalMem ?: 0L) / (1024 * 1024)
                Log.d(TAG, "Device memory: total=${totalMb}MB avail=${availMb}MB")
                when {
                    // avail >= 4GB: 4 threads (4 x ~100MB)
                    availMb >= 4096 -> 4
                    // avail >= 2GB: 3 threads
                    availMb >= 2048 -> 3
                    // low-end / tight: 2 threads fallback
                    else -> 2
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Failed to read memory info, fallback to 2 threads", e)
                2
            }
        }
    }

    override val archiveType: ArchiveType get() = ArchiveType.SEVEN_ZIP
    override val isPasswordProtected: Boolean get() = delegate.isPasswordProtected

    // ──────────────── Compression (official engine) ────────────────

    override suspend fun compress(
        sourceFiles: List<File>,
        destinationFile: File,
        password: String?,
        splitSize: Long?,
        onProgress: ((ArchiveExplorer.ExtractProgress) -> Unit)?,
    ) {
        // Both split / encrypted 7z go through the official engine (since 2026-08-17, no longer
        // falling back to commons-compress):
        // - Encryption: callback implements ICryptoGetTextPassword and returns the password; the 7z
        //   engine auto AES256-encrypts the content streams.
        //   (Header not encrypted, file names visible — same behavior as commons-compress output.)
        // - Split: SplitSevenZOutStream slices by splitSize (name.7z.001...), same shape as commons-compress's
        //   SplitSevenZChannel output, so the extract side needs no changes.
        val split = splitSize != null && splitSize > 0L
        val start = System.currentTimeMillis()
        val totalBytes = sourceFiles.sumOf { it.lengthRecursive() }
        Log.d(
            TAG,
            "compress start: sources=${sourceFiles.size} totalBytes=$totalBytes dest=${destinationFile.name} " +
                "splitSize=${if (split) splitSize else "none"} " +
                "password=${if (password.isNullOrEmpty()) "none" else "yes(len=${password.length})"}"
        )
        var outArchive: net.sf.sevenzipjbinding.IOutCreateArchive7z? = null
        var splitStream: SplitSevenZOutStream? = null
        var rafOut: RandomAccessFile? = null
        try {
            outArchive = SevenZip.openOutArchive7z()
            outArchive.setLevel(LEVEL)
            outArchive.setSolid(true)
            // ⚠️ Do NOT use 0 (auto = CPU core count): LZMA2 uses one dictionary buffer per thread,
            // low-end 8-core devices easily OOM. Limit to 2-4 threads by available memory.
            outArchive.setThreadCount(computeThreadCount(context))

            // Output stream: SplitSevenZOutStream for split (zero-copy slicing), RandomAccessFileOutStream otherwise.
            val outStream: IOutStream = if (splitSize != null && splitSize > 0L) {
                SplitSevenZOutStream(destinationFile, splitSize).also { splitStream = it }
            } else {
                RandomAccessFileOutStream(RandomAccessFile(destinationFile, "rw").also { rafOut = it })
            }

            // Flatten source list (recursively expand dirs into [file, relativePath]).
            val items = mutableListOf<Pair<File, String>>()
            sourceFiles.forEach { root ->
                addItem(root, root.name, items)
            }

            var lastReportAt = 0L
            val callback = object : IOutCreateCallback<IOutItem7z>, ICryptoGetTextPassword {
                // Provide password → 7z engine AES256-encrypts content streams; null/empty → no encryption.
                override fun cryptoGetTextPassword(): String? = password?.takeIf { it.isNotEmpty() }

                override fun setTotal(total: Long) {
                    Log.d(TAG, "setTotal: $total")
                }

                override fun setCompleted(complete: Long) {
                    val now = System.currentTimeMillis()
                    if (now - lastReportAt >= PROGRESS_REPORT_INTERVAL_MS) {
                        lastReportAt = now
                        onProgress?.invoke(
                            ArchiveExplorer.ExtractProgress(
                                currentBytes = complete,
                                totalBytes = totalBytes,
                                currentEntry = currentEntryName,
                            )
                        )
                    }
                }

                var currentEntryName: String = ""

                override fun getStream(index: Int): ISequentialInStream? {
                    if (index >= items.size) return null
                    val (file, _) = items[index]
                    currentEntryName = file.name
                    return if (file.isFile) {
                        val channel = FileChannel.open(file.toPath())
                        object : ISequentialInStream {
                            override fun read(data: java.nio.ByteBuffer, len: Int): Int {
                                // DirectByteBuffer fix: native-supplied DirectByteBuffer is read straight
                                // into by FileChannel → zero copy, zero JNI local-reference leak.
                                val oldLimit = data.limit()
                                if (len < oldLimit) data.limit(len)
                                val read = channel.read(data)
                                data.limit(oldLimit)
                                return if (read < 0) 0 else read
                            }
                            override fun close() {
                                try { channel.close() } catch (_: Throwable) {}
                            }
                        }
                    } else null
                }

                override fun setOperationResult(operationResultOk: Boolean) {
                    Log.d(TAG, "setOperationResult: ok=$operationResultOk")
                }

                override fun getItemInformation(
                    index: Int,
                    outItemFactory: OutItemFactory<IOutItem7z>,
                ): IOutItem7z {
                    val (file, relPath) = items[index]
                    val outItem = outItemFactory.createOutItem()
                    outItem.setPropertyPath(relPath)
                    outItem.setDataSize(if (file.isFile) file.length() else 0L)
                    outItem.setPropertyLastModificationTime(Date(file.lastModified()))
                    outItem.setPropertyIsDir(file.isDirectory)
                    outItem.setPropertyIsAnti(false)
                    outItem.setPropertyAttributes(0)
                    return outItem
                }
            }

            outArchive.createArchive(outStream, items.size, callback)

            val elapsed = System.currentTimeMillis() - start
            Log.d(
                TAG,
                "compress done: elapsed=${elapsed}ms rate=${totalBytes / elapsed.coerceAtLeast(1) / 1024f}KB/s " +
                    "output=${if (split) splitStream?.partCount().toString() + " volumes" else destinationFile.length().toString() + "B"} " +
                    "(pre=${totalBytes}B)"
            )
        } catch (e: Exception) {
            Log.e(TAG, "compress failed", e)
            cleanupSplitFiles(destinationFile, split)
            throw ArchiveException(
                "7z compress failed: ${e.message}",
                reason = ArchiveException.Reason.UNKNOWN,
                cause = e,
            )
        } finally {
            try { outArchive?.close() } catch (_: Throwable) {}
            try { splitStream?.close() } catch (_: Throwable) {}
            try { rafOut?.close() } catch (_: Throwable) {}
        }
    }

    /** Clean up already-generated `name.7z.0NN` split files on compression failure. */
    private fun cleanupSplitFiles(destinationFile: File, split: Boolean) {
        if (!split) {
            if (destinationFile.exists()) destinationFile.delete()
            return
        }
        val parent = destinationFile.parentFile ?: return
        val name = destinationFile.name
        parent.listFiles()?.forEach { f ->
            if (f.isFile && f.name.startsWith(name) &&
                f.name.removePrefix(name).matches(Regex("\\.7z\\.\\d+$"))
            ) {
                f.delete()
            }
        }
        if (destinationFile.exists()) destinationFile.delete()
    }

    private fun addItem(file: File, relPath: String, out: MutableList<Pair<File, String>>) {
        if (file.isDirectory) {
            out.add(file to relPath)
            file.listFiles()?.sorted()?.forEach { child ->
                addItem(child, "$relPath/${child.name}", out)
            }
        } else {
            out.add(file to relPath)
        }
    }

    private fun File.lengthRecursive(): Long {
        if (!isDirectory) return length()
        return listFiles()?.sumOf { it.lengthRecursive() } ?: 0L
    }

    // ──────────────── Browse/extract delegated to commons-compress ────────────────

    override suspend fun listRootEntries(password: String?): List<ArchiveEntryFileInfo> =
        delegate.listRootEntries(password)

    override suspend fun listChildEntries(
        directoryEntry: ArchiveEntryFileInfo,
        password: String?,
    ): List<ArchiveEntryFileInfo> = delegate.listChildEntries(directoryEntry, password)

    override suspend fun extractAll(
        destinationDir: File,
        password: String?,
        conflictStrategy: FileHelper.ConflictStrategy,
        onProgress: ((ArchiveExplorer.ExtractProgress) -> Unit)?,
    ) = delegate.extractAll(destinationDir, password, conflictStrategy, onProgress)

    override suspend fun extractEntries(
        entries: List<ArchiveEntryFileInfo>,
        destinationDir: File,
        password: String?,
        onProgress: ((ArchiveExplorer.ExtractProgress) -> Unit)?,
    ) = delegate.extractEntries(entries, destinationDir, password, onProgress)

    override suspend fun extractEntryToTemp(entry: ArchiveEntryFileInfo, password: String?): File =
        delegate.extractEntryToTemp(entry, password)

    override suspend fun openEntryStream(entryPath: String, password: String?): InputStream? =
        delegate.openEntryStream(entryPath, password)

    override fun close() = delegate.close()
}
