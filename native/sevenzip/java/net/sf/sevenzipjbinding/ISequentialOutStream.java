package net.sf.sevenzipjbinding;

import java.nio.ByteBuffer;

/**
 * Interface used to operate with sequential output stream.
 * 
 * @author Boris Brodski
 * @since 4.65-1
 */
public interface ISequentialOutStream {
    /**
     * Writes <code>len</code> bytes from <code>data</code> to the out-stream.<br>
     * <br>
     * <i>Note:</i> depending on the archive format and the data size this method may be called from different threads.
     * Synchronized implementation may be required.
     * 
     * @param buffer
     *            buffer with data to write (DirectByteBuffer backed by native memory)
     * @param len
     *            amount of bytes to write
     * 
     * @return amount of bytes written
     * 
     * @throws SevenZipException
     *             in error case. If this method ends with an exception, the current operation will be reported to 7-Zip
     *             as failed. There are no guarantee, that there are no further call back methods will get called. The
     *             first and last thrown exceptions will be saved and thrown later on from the originally called method
     *             such as <code>ISevenZipInArchive.extract()</code> or <code>SevenZip.openInArchive()</code>. Up to
     *             four exceptions depending on the situation can be saved for further analysis. See
     *             {@link SevenZipException} and {@link SevenZipException#printStackTraceExtended()} for details.
     */
    public int write(ByteBuffer buffer, int len) throws SevenZipException;
}
