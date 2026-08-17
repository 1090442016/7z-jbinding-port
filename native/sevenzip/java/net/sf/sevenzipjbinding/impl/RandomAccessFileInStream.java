package net.sf.sevenzipjbinding.impl;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

import net.sf.sevenzipjbinding.IInStream;
import net.sf.sevenzipjbinding.SevenZipException;

/**
 * Implementation of {@link IInStream} using {@link RandomAccessFile}.
 * 
 * @author Boris Brodski
 * @since 4.65-1
 */
public class RandomAccessFileInStream implements IInStream {
    private final RandomAccessFile randomAccessFile;

    /**
     * Constructs instance of the class from random access file.
     * 
     * @param randomAccessFile
     *            random access file to use
     */
    public RandomAccessFileInStream(RandomAccessFile randomAccessFile) {
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
     * <p>
     * DirectByteBuffer 修复（2026-08-17）：native 层用 NewDirectByteBuffer 包装 native 内存，
     * 这里通过 FileChannel 直接读入 → 零复制、零 JNI 局部引用泄漏。
     */
    public synchronized int read(ByteBuffer data, int len) throws SevenZipException {
        try {
            // 确保只读 len 字节
            int oldLimit = data.limit();
            if (len < oldLimit) {
                data.limit(len);
            }
            int read = randomAccessFile.getChannel().read(data);
            data.limit(oldLimit);
            return read < 0 ? 0 : read;
        } catch (IOException e) {
            throw new SevenZipException("Error reading random access file", e);
        }
    }

    /**
     * Closes random access file. After this call no more methods should be called.
     * 
     * @throws IOException
     *             see {@link RandomAccessFile#close()}
     */
    public synchronized void close() throws IOException {
        randomAccessFile.close();
    }
}
