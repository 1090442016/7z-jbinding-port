package net.sf.sevenzipjbinding.impl;

import java.io.Closeable;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

import net.sf.sevenzipjbinding.IOutStream;
import net.sf.sevenzipjbinding.SevenZipException;

/**
 * Implementation of {@link IOutStream} using {@link RandomAccessFile}.
 * 
 * @author Boris Brodski
 * @since 4.65-1
 */
public class RandomAccessFileOutStream implements IOutStream, Closeable {
    private final RandomAccessFile randomAccessFile;

    /**
     * Constructs instance of the class from random access file.
     * 
     * @param randomAccessFile
     *            random access file to use
     */
    public RandomAccessFileOutStream(RandomAccessFile randomAccessFile) {
        this.randomAccessFile = randomAccessFile;
    }

    /**
     * {@inheritDoc}
     */
    public synchronized long seek(long offset, int seekOrigin) throws SevenZipException {
        try {
            switch (seekOrigin) {
            case SEEK_SET:
                randomAccessFile.seek(offset);
                break;

            case SEEK_CUR:
                randomAccessFile.seek(randomAccessFile.getFilePointer() + offset);
                break;

            case SEEK_END:
                randomAccessFile.seek(randomAccessFile.length() + offset);
                break;

            default:
                throw new RuntimeException("Seek: unknown origin: " + seekOrigin);
            }

            return randomAccessFile.getFilePointer();
        } catch (IOException e) {
            throw new SevenZipException("Error while seek operation", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void setSize(long newSize) throws SevenZipException {
        try {
            randomAccessFile.setLength(newSize);
        } catch (IOException exception) {
            throw new SevenZipException("Error setting new length of the file", exception);
        }

    }

    /**
     * {@inheritDoc}
     * <p>
     * DirectByteBuffer 修复（2026-08-17）：native 层用 NewDirectByteBuffer 包装 native 内存，
     * 这里通过 FileChannel 直接写出 → 零复制、零 JNI 局部引用泄漏。
     */
    public synchronized int write(ByteBuffer buffer, int len) throws SevenZipException {
        try {
            int oldLimit = buffer.limit();
            if (len < oldLimit) {
                buffer.limit(len);
            }
            int written = randomAccessFile.getChannel().write(buffer);
            buffer.limit(oldLimit);
            return written;
        } catch (IOException exception) {
            throw new SevenZipException("Error writing random access file", exception);
        }
    }

    /**
     * Closes random access file. After this call no more methods should be called.
     * 
     * @throws IOException
     *             see {@link RandomAccessFile#close()}
     */
    public void close() throws IOException {
        randomAccessFile.close();
    }
}
