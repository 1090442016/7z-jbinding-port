package net.sf.sevenzipjbinding.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import net.sf.sevenzipjbinding.ISequentialInStream;
import net.sf.sevenzipjbinding.SevenZipException;

/**
 * Input stream based implementation of {@link ISequentialInStream}.
 * 
 * @author Boris Brodski
 * @since 9.20-2.00
 */
public class InputStreamSequentialInStream implements ISequentialInStream {
    private final InputStream inputStream;

    /**
     * Create new input stream based implementation of {@link ISequentialInStream}.
     * 
     * @param inputStream
     *            base input stream.
     */
    public InputStreamSequentialInStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    /**
     * {@inheritDoc}
     * <p>
     * DirectByteBuffer 修复（2026-08-17）：native 层用 NewDirectByteBuffer 包装 native 内存，
     * 这里从 InputStream 读出后 put 进 ByteBuffer（仅当 InputStream 不支持 channel 时，
     * 主路径用 RandomAccessFileInStream 走 FileChannel 零复制）。
     */
    public int read(ByteBuffer data, int len) throws SevenZipException {
        if (len == 0) {
            return 0;
        }

        try {
            if (data.hasRemaining()) {
                byte[] temp = new byte[Math.min(len, data.remaining())];
                int result = inputStream.read(temp);
                if (result < 0) {
                    return 0;
                }
                data.put(temp, 0, result);
                return result;
            }
            return 0;
        } catch (IOException e) {
            throw new SevenZipException("Error reading " + len + " bytes out of InputStream", e);
        }
    }

    /**
     * Returns base input stream
     * 
     * @return input stream
     */
    public InputStream getInputStream() {
        return inputStream;
    }

    public void close() throws IOException {
        inputStream.close();
    }
}
