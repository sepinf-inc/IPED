package dpf.sp.gpinf.indexer.util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.HashMap;
import java.util.HashSet;

import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.LocalConfig;

public class SleuthkitServer {
    
    public static final int MMAP_FILE_SIZE = 10 * 1024 * 1024;
    private static final int MAX_BUF_SIZE = 8 * 1024 * 1024; //must be less than MMAP_FILE_SIZE

    static class FLAGS {

        // client cmds
        static byte READ = 2;
        static byte SEEK = 3;
        static byte SIZE = 7;
        static byte POSITION = 8;
        static byte CLOSE = 9;
        // server responses
        static byte DONE = 4;
        static byte ERROR = 5;
        static byte EOF = 6;
        static byte EXCEPTION = 10;
        // temp state
        static byte SQLITE_READ = 11;
        
        final static boolean isClientCmd(int cmd) {
            return cmd != FLAGS.DONE && cmd != FLAGS.ERROR && cmd != FLAGS.EOF && cmd != FLAGS.EXCEPTION && cmd != FLAGS.SQLITE_READ;
        }
    }
    
    static boolean useUnsafe = true;

    public static void main(String args[]) {

        String dbPath = args[0];
        String id = args[1];
        String pipePath = args[2];
        MappedByteBuffer out = null;
        
        InputStream in = System.in;
        OutputStream os = System.out;
        System.setIn(new ByteArrayInputStream(new byte[0]));
        System.setOut(System.err);
        
        try {
            int size = MMAP_FILE_SIZE;
            RandomAccessFile raf = new RandomAccessFile(pipePath, "rw"); //$NON-NLS-1$
            raf.setLength(size);
            FileChannel fc = raf.getChannel();
            out = fc.map(MapMode.READ_WRITE, 0, size);
            out.load();
            
            Configuration.getInstance().loadConfigurables(new File(dbPath).getParent() + "/indexador"); //$NON-NLS-1$
            ConfigurationManager cm = ConfigurationManager.getInstance();
            LocalConfig localConfig = new LocalConfig();
            cm.addObject(localConfig);
            cm.loadConfigs();
            Configuration.getInstance().loadLibsAndToolPaths();
            
            SleuthkitCase sleuthCase = SleuthkitCase.openCase(dbPath);
            HashMap<Long, SleuthkitInputStream> sisMap = new HashMap<>();

            java.util.logging.Logger.getLogger("org.sleuthkit").setLevel(java.util.logging.Level.SEVERE); //$NON-NLS-1$

            commitByte(out, 0, FLAGS.DONE);
            notify(os);

            byte[] buf = new byte[MAX_BUF_SIZE];
            int minToRead = 64 * 1024;
            SleuthkitInputStream sis = null;

            while (true) {
                try {
                    int read = in.read();
                    if(read == -1)
                        break;
                    if(read > 0) {
                        //ping response
                        os.write(read);
                        os.flush();
                        continue;
                    }
                    byte cmd = waitCmd(out, in);
                    sis = getSis(out, sleuthCase, sisMap);
                    commitByte(out, 0, FLAGS.SQLITE_READ);

                    if (cmd == FLAGS.SEEK) {
                        sis.seek(out.getLong(13));
                    } else if (cmd == FLAGS.CLOSE) {
                        sis = sisMap.remove(out.getLong(5));
                        sis.close();
                    } else if (cmd == FLAGS.READ) {
                        int len = out.getInt(13);
                        len = Math.max(minToRead, Math.min(len, buf.length));
                        len = readIn(sis, buf, len);
                        if (len == -1) {
                            commitByte(out, 0, FLAGS.EOF);
                            notify(os);
                            continue;
                        } else {
                            writeOut(out, buf, len);
                        }
                    } else if (cmd == FLAGS.SIZE) {
                        out.putLong(13, sis.size());
                    } else if (cmd == FLAGS.POSITION) {
                        out.putLong(13, sis.position());
                    }

                    commitByte(out, 0, FLAGS.DONE);
                    notify(os);

                } catch (Throwable e) {
                    // e.printStackTrace(System.err);
                    byte[] msgBytes = e.getMessage().getBytes("UTF-8"); //$NON-NLS-1$
                    out.putInt(13, msgBytes.length);
                    out.position(17);
                    out.put(msgBytes);
                    commitByte(out, 0, FLAGS.EXCEPTION);
                    notify(os);
                }
            }

        } catch (Throwable e) {
            e.printStackTrace();
            commitByte(out, 0, FLAGS.ERROR);
            try {
                if (os != null)
                    notify(os);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

    private static HashSet<Long> warmedDataSources = new HashSet<>();

    private static SleuthkitInputStream getSis(MappedByteBuffer out, SleuthkitCase sleuthCase, HashMap<Long, SleuthkitInputStream> sisMap) throws Exception {
        long streamId = out.getLong(5);
        SleuthkitInputStream sis = sisMap.get(streamId);
        if (sis == null) {
            int id = out.getInt(1);
            Content content = sleuthCase.getAbstractFileById(id);
            if (content == null) {
                content = sleuthCase.getContentById(id);
            }
            sis = new SleuthkitInputStream(content);
            sisMap.put(streamId, sis);

            // first read can take a long time, so do it here to prevent timeouts on client
            // side
            Long sourceId = content.getDataSource().getId();
            if (!warmedDataSources.contains(sourceId)) {
                sis.read();
                sis.seek(0);
                warmedDataSources.add(sourceId);
            }
        }
        return sis;
    }

    private static byte waitCmd(MappedByteBuffer out, InputStream in) throws Exception {
        byte cmd;
        long t = 0;
        while (!FLAGS.isClientCmd(cmd = getByte(out, 0))) {
            if(t == 0)
                t = System.currentTimeMillis();
            long time = (System.currentTimeMillis() - t) / 1000; 
            if(time >= 10)
                throw new IOException("MemoryReadTimeout waiting SleuthkitClient!"); //$NON-NLS-1$
            Thread.sleep(1);
        }
        return cmd;
    }

    private static int readIn(SleuthkitInputStream sis, byte[] buf, int len) throws IOException {
        return sis.read(buf, 0, len);
    }

    private static void writeOut(MappedByteBuffer out, byte[] buf, int len) throws Exception {
        out.position(17);
        out.put(buf, 0, len);
        out.putInt(13, len);
    }

    static void notify(OutputStream os) throws IOException {
        os.write(0);
        os.flush();
    }

    static final void commitByte(MappedByteBuffer mbb, int pos, byte val) {
        if (useUnsafe) {
            try {
                DirectMemory.putByteVolatile(mbb, pos, val);
                return;

            } catch (Throwable e) {
                useUnsafe = false;
            }
        }

        mbb.put(pos, val);

    }

    static final Byte getByte(MappedByteBuffer mbb, int pos) {
        if (useUnsafe) {
            try {
                return DirectMemory.getByteVolatile(mbb, pos);
            } catch (Throwable e) {
                useUnsafe = false;
            }
        }

        return mbb.get(pos);
    }

}
