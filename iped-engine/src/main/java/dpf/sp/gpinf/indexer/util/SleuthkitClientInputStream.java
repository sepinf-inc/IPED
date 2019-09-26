package dpf.sp.gpinf.indexer.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.SocketTimeoutException;
import java.nio.MappedByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.util.SleuthkitServer.FLAGS;
import iped3.io.SeekableInputStream;

public class SleuthkitClientInputStream extends SeekableInputStream {

    private static Logger LOGGER = LoggerFactory.getLogger(SleuthkitClientInputStream.class);

    private static AtomicLong next = new AtomicLong();

    private static int TIMEOUT = 10000;

    int sleuthId;
    String path;
    SleuthkitClient client;
    long streamId = next.getAndIncrement();
    private InputStream in;
    OutputStream os;
    int bufPos = 0;
    byte[] buf;
    MappedByteBuffer mbb;
    boolean closed = false, empty = true;
    long position = 0;
    Long size;

    public SleuthkitClientInputStream(int id, String path, SleuthkitClient client) {
        this.sleuthId = id;
        this.path = path;
        this.client = client;
        this.mbb = client.out;
        this.in = client.is;
        this.os = client.os;
    }

    @Override
    public int read(byte b[], int off, int len) throws IOException {

        if (closed) {
            throw new IOException("Stream is closed!"); //$NON-NLS-1$
        }
        if (client.serverError) {
            throw new IOException("SleuthkitServer returned an error before."); //$NON-NLS-1$
        }

        int read = readIn(b, off, len);
        return read;

    }

    private int readIn(byte b[], int off, int len) throws IOException {

        if (empty) {
            synchronized (client) {
                byte cmd = sendRead();
                if (cmd == FLAGS.EOF) {
                    return -1;
                }
                int size = mbb.getInt(13);
                buf = new byte[size];
                mbb.position(17);
                mbb.get(buf, 0, size);
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

    private byte sendRead() throws IOException {
        mbb.putInt(1, sleuthId);
        mbb.putLong(5, streamId);
        SleuthkitServer.commitByte(mbb, 0, FLAGS.READ);
        notifyServer();
        return waitServerResponse();
    }

    private byte waitServerResponse() throws IOException {
        boolean sqliteBusy = true;
        while (sqliteBusy) {
            try {
                in.read();
                sqliteBusy = false;

            } catch (SocketTimeoutException e) {
                if (SleuthkitServer.getByte(mbb, 0) != FLAGS.SQLITE_READ) {
                    LOGGER.info("Waiting SleuthkitServer open file " + path); //$NON-NLS-1$
                    continue;
                }

                client.serverError = true;
                LOGGER.error("SocketTimeout waiting SleuthkitServer: " + path); //$NON-NLS-1$
                throw e;

            } catch (IOException e1) {
                client.serverError = true;
                LOGGER.error(getCrashMsg());
                throw e1;
            }
        }

        byte cmd;
        long time = 0;
        while (FLAGS.isClientCmd(cmd = SleuthkitServer.getByte(mbb, 0))) {
            try {
                if (time == 0) {
                    time = System.currentTimeMillis();
                }
                Thread.sleep(1);
                LOGGER.error("Waiting Server memory write..."); //$NON-NLS-1$

                if (System.currentTimeMillis() - time >= TIMEOUT) {
                    client.serverError = true;
                    LOGGER.error("MemoryReadTimeout waiting SleuthkitServer: " + path); //$NON-NLS-1$
                    throw new IOException("MemoryReadTimeout waiting SleuthkitServer: " + path); //$NON-NLS-1$
                }

            } catch (InterruptedException e) {
                throw new InterruptedIOException(e.toString());
            }
        }

        if (cmd == FLAGS.EXCEPTION) {
            int len = mbb.getInt(13);
            byte[] b = new byte[len];
            mbb.position(17);
            mbb.get(b);
            try {
                throw new IOException("SleuthkitServer error: " + new String(b, "UTF-8")); //$NON-NLS-1$ //$NON-NLS-2$
            } catch (UnsupportedEncodingException e) {
            }
        }

        return cmd;
    }

    private void notifyServer() throws IOException {
        try {
            SleuthkitServer.notify(os);
        } catch (IOException e) {
            client.serverError = true;
            LOGGER.error(getCrashMsg());
            throw e;
        }
    }

    private String getCrashMsg() {
        return "Possible Sleuthkit Crash reading " + path; //$NON-NLS-1$
    }

    @Override
    public void seek(long pos) throws IOException {

        if (closed) {
            throw new IOException("Stream is closed!"); //$NON-NLS-1$
        }
        if (client.serverError) {
            throw new IOException("SleuthkitServer returned an error before."); //$NON-NLS-1$
        }
        
        long dif = pos - position;
        if(!empty && bufPos + dif >= 0 && bufPos + dif < buf.length) {
            bufPos += dif;
        
        }else synchronized (client) {
            mbb.putInt(1, sleuthId);
            mbb.putLong(5, streamId);
            mbb.putLong(13, pos);
            SleuthkitServer.commitByte(mbb, 0, FLAGS.SEEK);
            notifyServer();
            waitServerResponse();
            empty = true;
            bufPos = 0;
        }
        
        position = pos;

    }

    @Override
    public long position() throws IOException {
        return position;
    }

    @Override
    public long size() throws IOException {
        
        if(size != null)
            return size;

        if (closed) {
            throw new IOException("Stream is closed!"); //$NON-NLS-1$
        }
        if (client.serverError) {
            throw new IOException("SleuthkitServer returned an error before."); //$NON-NLS-1$
        }

        synchronized (client) {
            mbb.putInt(1, sleuthId);
            mbb.putLong(5, streamId);
            SleuthkitServer.commitByte(mbb, 0, FLAGS.SIZE);
            notifyServer();
            waitServerResponse();
            size = mbb.getLong(13);
            return size;
        }

    }

    @Override
    public int read() throws IOException {
        if (closed) {
            throw new IOException("Stream is closed!"); //$NON-NLS-1$
        }
        if (client.serverError) {
            throw new IOException("SleuthkitServer returned an error before."); //$NON-NLS-1$
        }

        byte[] b = new byte[1];
        int i = 0;
        do {
            i = read(b);
        } while (i == 0);

        if (i == -1) {
            return -1;
        }

        return b[0];
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }

        if (client.serverError) {
            return;
        }

        synchronized (client) {
            closed = true;
            mbb.putInt(1, sleuthId);
            mbb.putLong(5, streamId);
            SleuthkitServer.commitByte(mbb, 0, FLAGS.CLOSE);
            empty = true;
            notifyServer();
            waitServerResponse();
            client.removeStream(streamId);
        }

    }

}
