package iped.utils;

import javax.imageio.stream.ImageInputStreamImpl;

public final class ByteArrayImageInputStream extends ImageInputStreamImpl {
    private final byte[] data;

    public ByteArrayImageInputStream(byte[] data) {
        this.data = data;
    }

    @Override
    public int read() {
        if (streamPos >= data.length) {
            return -1;
        }
        bitOffset = 0;
        return data[(int) streamPos++] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) {
        if (streamPos >= data.length) {
            return -1;
        }
        len = Math.min(data.length - (int) streamPos, len);
        System.arraycopy(data, (int) streamPos, b, off, len);
        streamPos += len;
        bitOffset = 0;
        return len;
    }

    @Override
    public long length() {
        return data.length;
    }

    @Override
    public boolean isCached() {
        return true;
    }

    @Override
    public boolean isCachedMemory() {
        return true;
    }
}
