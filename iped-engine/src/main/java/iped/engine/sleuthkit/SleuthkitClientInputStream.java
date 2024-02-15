package iped.engine.sleuthkit;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.engine.sleuthkit.SleuthkitServer.FLAGS;
import iped.io.SeekableInputStream;

public class SleuthkitClientInputStream extends SeekableInputStream {

    private static Logger LOGGER = LoggerFactory.getLogger(SleuthkitClientInputStream.class);

    private static AtomicLong next = new AtomicLong();

    private static int TIMEOUT = 10000;

    int sleuthId;
    String path;
    SleuthkitClient client;
    long streamId = next.getAndIncrement();
    int bufPos = 0;
    byte[] buf;
    boolean closed = false, empty = true;
    long position = 0;
    Long size;
    boolean seekAfterRestart = false;

    public SleuthkitClientInputStream(int id, String path, SleuthkitClient client) {
        this.sleuthId = id;
        this.path = path;
        this.client = client;
    }

    private String getServerId() {
        return "SleuthkitServer " + client.id;
    }

    @Override
    public int read(byte b[], int off, int len) throws IOException {

        if (closed) {
            throw new IOException("Stream is closed!"); //$NON-NLS-1$
        }

        int read = readIn(b, off, len);
        return read;

    }

    private int readIn(byte b[], int off, int len) throws IOException {

        if (empty) {
            synchronized (client) {
                if (client.isServerError()) {
                    client.restartServer();
                }
                if (seekAfterRestart) {
                    seek(position);
                    seekAfterRestart = false;
                }
                byte cmd = sendRead(len);
                if (cmd == FLAGS.EOF) {
                    return -1;
                }
                int size = client.mbb.getInt(13);
                buf = new byte[size];
                client.mbb.position(17);
                client.mbb.get(buf, 0, size);
                bufPos = 0;
                empty = false;
            }
        }

        int copyLen = Math.min(len, buf.length - bufPos);
        System.arraycopy(buf, bufPos, b, off, copyLen);

        bufPos += copyLen;
        if (bufPos == buf.length) {
            empty = true;
        }
        position += copyLen;

        return copyLen;
    }

    private byte sendRead(int len) throws IOException {
        client.mbb.putInt(1, sleuthId);
        client.mbb.putLong(5, streamId);
        client.mbb.putInt(13, len);
        SleuthkitServer.commitByte(client.mbb, 0, FLAGS.READ);
        notifyServer();
        return waitServerResponse();
    }

    private byte waitServerResponse() throws IOException {

        client.enableTimeoutCheck(true);
        try {
            int b = client.is.read();
            if (b == -1)
                throw new IOException(getServerId() + " pipe closed!"); //$NON-NLS-1$

        } catch (IOException e) {
            client.setServerError(true);
            LOGGER.error("Wait response error: " + getCrashMsg());
            throw e;

        } finally {
            client.enableTimeoutCheck(false);
        }

        byte cmd;
        long time = 0;
        while (FLAGS.isClientCmd(cmd = SleuthkitServer.getByte(client.mbb, 0)) || cmd == FLAGS.SQLITE_READ) {
            try {
                if (time == 0) {
                    time = System.currentTimeMillis();
                }
                Thread.sleep(1);
                LOGGER.warn("Waiting " + getServerId() + " memory write..."); //$NON-NLS-1$

                if (System.currentTimeMillis() - time >= TIMEOUT) {
                    client.setServerError(true);
                    LOGGER.error("MemoryReadTimeout waiting " + getServerId() + ": " + path); //$NON-NLS-1$
                    throw new IOException("MemoryReadTimeout waiting " + getServerId() + ": " + path); //$NON-NLS-1$
                }

            } catch (InterruptedException e) {
                throw new InterruptedIOException(e.toString());
            }
        }

        if (cmd == FLAGS.EXCEPTION) {
            int len = client.mbb.getInt(13);
            byte[] b = new byte[len];
            client.mbb.position(17);
            client.mbb.get(b);
            try {
                throw new IOException(getServerId() + " error: " + new String(b, "UTF-8")); //$NON-NLS-1$ //$NON-NLS-2$
            } catch (UnsupportedEncodingException e) {
            }
        }

        return cmd;
    }

    private void notifyServer() throws IOException {
        try {
            SleuthkitServer.notify(client.os);
        } catch (IOException e) {
            client.setServerError(true);
            LOGGER.error("Notify error: " + getCrashMsg());
            throw e;
        }
    }

    private String getCrashMsg() {
        return "Possible " + getServerId() + " crash reading " + path; //$NON-NLS-1$
    }

    @Override
    public void seek(long pos) throws IOException {

        if (closed) {
            throw new IOException("Stream is closed!"); //$NON-NLS-1$
        }

        long dif = pos - position;
        if (!empty && bufPos + dif >= 0 && bufPos + dif < buf.length) {
            bufPos += dif;

        } else {
            synchronized (client) {
                if (client.isServerError()) {
                    client.restartServer();
                }
                client.mbb.putInt(1, sleuthId);
                client.mbb.putLong(5, streamId);
                client.mbb.putLong(13, pos);
                SleuthkitServer.commitByte(client.mbb, 0, FLAGS.SEEK);
                notifyServer();
                waitServerResponse();
                empty = true;
                bufPos = 0;
                seekAfterRestart = false;
            }
        }
        position = pos;

    }

    @Override
    public long position() throws IOException {
        return position;
    }

    @Override
    public long size() throws IOException {

        if (size != null)
            return size;

        if (closed) {
            throw new IOException("Stream is closed!"); //$NON-NLS-1$
        }

        synchronized (client) {
            if (client.isServerError()) {
                client.restartServer();
            }
            client.mbb.putInt(1, sleuthId);
            client.mbb.putLong(5, streamId);
            SleuthkitServer.commitByte(client.mbb, 0, FLAGS.SIZE);
            notifyServer();
            waitServerResponse();
            size = client.mbb.getLong(13);
            return size;
        }

    }

    @Override
    public int read() throws IOException {

        if (closed) {
            throw new IOException("Stream is closed!"); //$NON-NLS-1$
        }

        byte[] b = new byte[1];
        int i = 0;
        do {
            i = read(b);
        } while (i == 0);

        if (i == -1) {
            return -1;
        }

        return b[0] & 0xFF;
    }

    @Override
    public void close() throws IOException {

        if (closed) {
            return;
        }
        synchronized (client) {
            if (!client.isServerError()) {
                client.mbb.putInt(1, sleuthId);
                client.mbb.putLong(5, streamId);
                SleuthkitServer.commitByte(client.mbb, 0, FLAGS.CLOSE);
                notifyServer();
                waitServerResponse();
            }
            client.removeStream(this);
        }
        empty = true;
        closed = true;

    }

}
