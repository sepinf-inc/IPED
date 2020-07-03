package dpf.sp.gpinf.indexer.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tika.utils.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.config.ConfigurationManager;
import dpf.sp.gpinf.indexer.config.LocalConfig;
import dpf.sp.gpinf.indexer.config.SleuthKitConfig;
import dpf.sp.gpinf.indexer.datasource.SleuthkitReader;
import dpf.sp.gpinf.indexer.util.SleuthkitServer.FLAGS;
import iped3.io.SeekableInputStream;

public class SleuthkitClient implements Comparable<SleuthkitClient> {

    private static Logger logger = LoggerFactory.getLogger(SleuthkitClient.class);

    private static final int MAX_STREAMS = 10000;
    private static final int TIMEOUT_SECONDS = 3600;

    private static PriorityQueue<SleuthkitClient> clientPriorityQueue = new PriorityQueue<>();
    private static Object lock = new Object();

    private static List<SleuthkitClient> clientsList = new ArrayList<>();

    public static int NUM_TSK_SERVERS;

    static volatile String dbDirPath;
    static AtomicInteger idStart = new AtomicInteger();

    static {
        SleuthKitConfig config = (SleuthKitConfig) ConfigurationManager.getInstance().findObjects(SleuthKitConfig.class)
                .iterator().next();
        NUM_TSK_SERVERS = config.getNumImageReaders();
    }

    Process process;
    int id = idStart.getAndIncrement();;
    InputStream is;
    FileChannel fc;
    File pipe;
    MappedByteBuffer out;
    OutputStream os;
    Random rand = new Random();

    volatile boolean serverError = false;

    private int openedStreams = 0;
    private Set<Long> currentStreams = new HashSet<>();
    private int priority = 0;
    private volatile long requestTime = 0;

    static class TimeoutMonitor extends Thread {
        public void run() {
            try {
                while (true) {
                    Thread.sleep(5000);
                    for (SleuthkitClient client : clientsList) {
                        client.checkTimeout();
                    }
                }
            } catch (InterruptedException e) {
            }
        }
    }

    private void checkTimeout() {
        if (requestTime == 0)
            return;
        if (SleuthkitServer.getByte(out, 0) != FLAGS.SQLITE_READ) {
            logger.info("Waiting SleuthkitServer database read..."); //$NON-NLS-1$
            return;
        }
        if (System.currentTimeMillis() / 1000 - requestTime >= TIMEOUT_SECONDS) {
            logger.error("Timeout waiting SleuthkitServer " + id + " response! Restarting...");
            if (process != null) {
                process.destroyForcibly();
            }
            serverError = true;
            requestTime = 0;
        }
    }

    public void enableTimeoutCheck(boolean enable) {
        if (enable)
            requestTime = System.currentTimeMillis() / 1000;
        else
            requestTime = 0;
    }

    public static SleuthkitClient get() {

        synchronized (lock) {
            SleuthkitClient sc = clientPriorityQueue.poll();
            sc.priority++;
            clientPriorityQueue.add(sc);
            return sc;
        }

    }

    public static void initSleuthkitServers(final String dbPath) throws InterruptedException {
        dbDirPath = dbPath;
        ArrayList<Thread> initThreads = new ArrayList<>();
        for (int i = 0; i < NUM_TSK_SERVERS; i++) {
            Thread t = new Thread() {
                @Override
                public void run() {
                    SleuthkitClient sc = new SleuthkitClient();
                    synchronized (lock) {
                        clientPriorityQueue.add(sc);
                        clientsList.add(sc);
                    }
                }
            };
            t.start();
            initThreads.add(t);
        }
        for (Thread t : initThreads) {
            t.join();
        }
        Thread t = new TimeoutMonitor();
        t.setDaemon(true);
        t.start();
    }

    public static void shutDownServers() {
        for (SleuthkitClient sc : clientsList)
            sc.finishProcessAndClearMmap();
    }

    private SleuthkitClient() {
        while (process == null || !isAlive(process)) {
            start();
        }
    }

    private void start() {

        LocalConfig localConfig = (LocalConfig) ConfigurationManager.getInstance().findObjects(LocalConfig.class)
                .iterator().next();
        String pipePath = localConfig.getIndexerTemp() + "/pipe-" + id; //$NON-NLS-1$

        String classpath = Configuration.getInstance().appRoot + "/lib/*"; //$NON-NLS-1$
        if (Configuration.getInstance().tskJarFile != null) {
            classpath += SystemUtils.IS_OS_WINDOWS ? ";" : ":";
            classpath += Configuration.getInstance().tskJarFile.getAbsolutePath(); // $NON-NLS-1$
        }

        String[] cmd = { "java", "-cp", classpath, "-Xmx128M", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                SleuthkitServer.class.getCanonicalName(), dbDirPath + "/" + SleuthkitReader.DB_NAME, //$NON-NLS-1$
                String.valueOf(id), pipePath };

        try {
            logger.info("Starting SleuthkitServer " + id + ": " + Arrays.asList(cmd));

            ProcessBuilder pb = new ProcessBuilder(cmd);
            process = pb.start();

            logStdErr(process.getErrorStream(), id);

            is = process.getInputStream();
            os = process.getOutputStream();

            int size = SleuthkitServer.MMAP_FILE_SIZE;
            pipe = new File(pipePath);
            try (RandomAccessFile raf = new RandomAccessFile(pipePath, "rw")) { //$NON-NLS-1$
                raf.setLength(size);
                fc = raf.getChannel();
                out = fc.map(MapMode.READ_WRITE, 0, size);
                out.load();
            } catch (ClosedByInterruptException e) {
                // clear interrupt status
                Thread.interrupted();
                throw e;
            }

            is.read();
            boolean ok = false;
            while (!(ok = SleuthkitServer.getByte(out, 0) == FLAGS.DONE)
                    && SleuthkitServer.getByte(out, 0) != FLAGS.ERROR) {
                Thread.sleep(1);
            }

            if (!ok) {
                throw new Exception("Error starting SleuthkitServer"); //$NON-NLS-1$
            }

        } catch (Exception e) {
            e.printStackTrace();
            if (process != null) {
                process.destroyForcibly();
            }
            process = null;
        }
    }

    private void logStdErr(final InputStream is, final int id) {
        new Thread() {
            public void run() {
                byte[] b = new byte[1024 * 1024];
                try {
                    int r = 0;
                    while ((r = is.read(b)) != -1) {
                        String msg = new String(b, 0, r).trim();
                        if (!msg.isEmpty())
                            logger.info("SleuthkitServer " + id + ": " + msg); //$NON-NLS-1$ //$NON-NLS-2$
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    private boolean ping() {
        int i = rand.nextInt(255) + 1;
        try {
            os.write(i);
            os.flush();
            int r = is.read();
            if (r == i)
                return true;

        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public synchronized SeekableInputStream getInputStream(int id, String path) throws IOException {

        if (!serverError && !ping()) {
            logger.warn("Ping SleuthkitServer " + this.id + " failed! Restarting..."); //$NON-NLS-1$ //$NON-NLS-2$
            serverError = true;
        }

        if (serverError || (openedStreams > MAX_STREAMS && currentStreams.size() == 0)) {
            if (process != null) {
                process.destroyForcibly();
            }
            process = null;
            if (!serverError)
                logger.info("Restarting SleuthkitServer to clean possible resource leaks."); //$NON-NLS-1$
            serverError = false;
            openedStreams = 0;
            currentStreams.clear();
            synchronized (lock) {
                priority = 1;
            }
        }

        while (process == null || !isAlive(process)) {
            start();
        }

        SleuthkitClientInputStream stream = new SleuthkitClientInputStream(id, path, this);

        currentStreams.add(stream.streamId);
        openedStreams++;

        return stream;
    }

    public synchronized void removeStream(long streamID) {
        boolean removed = currentStreams.remove(streamID);
        if (removed) {
            synchronized (lock) {
                clientPriorityQueue.remove(this);
                priority--;
                clientPriorityQueue.add(this);
            }
        }
    }

    private void finishProcessAndClearMmap() {
        process.destroyForcibly();
        try {
            fc.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        fc = null;
        out = null;
        int tries = 100;
        do {
            System.gc();
            logger.info("Trying to delete " + pipe.getAbsolutePath());
        } while (!pipe.delete() && tries-- > 0);
    }

    private boolean isAlive(Process p) {
        try {
            p.exitValue();
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    @Override
    public int compareTo(SleuthkitClient o) {
        return priority - o.priority;
    }
}
