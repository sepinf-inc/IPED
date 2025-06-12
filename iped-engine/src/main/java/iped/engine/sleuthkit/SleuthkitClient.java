package iped.engine.sleuthkit;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tika.utils.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.engine.config.Configuration;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.FileSystemConfig;
import iped.engine.config.LocalConfig;
import iped.engine.config.PluginConfig;
import iped.engine.core.Manager;
import iped.engine.sleuthkit.SleuthkitServer.FLAGS;
import iped.io.SeekableInputStream;

public class SleuthkitClient implements Comparable<SleuthkitClient> {

    private static Logger logger = LoggerFactory.getLogger(SleuthkitClient.class);

    private static final int MAX_STREAMS = 10000;
    private static final int TIMEOUT_SECONDS = 3600;

    private static PriorityQueue<SleuthkitClient> clientPriorityQueue = new PriorityQueue<>();
    private static Object lock = new Object();

    private static List<SleuthkitClient> clientsList = new ArrayList<>();

    private static final int NUM_TSK_SERVERS;

    private static final HashMap<String, String> newEnvVars = new HashMap<>();

    private static volatile File tskDb;
    private static AtomicInteger idStart = new AtomicInteger();

    private static final AtomicBoolean initSleuthkitServers = new AtomicBoolean(false);

    static {
        if (Manager.getInstance() != null) {
            FileSystemConfig config = ConfigurationManager.get().findObject(FileSystemConfig.class);
            NUM_TSK_SERVERS = config.getNumImageReaders();
        } else {
            // Analysis UI just needs 1 process in most scenarios (gallery could benefit of
            // more processes just if thumbs are not pre-computed)
            NUM_TSK_SERVERS = 1;
        }
    }

    int id = idStart.getAndIncrement();;
    Process process;
    InputStream is;
    FileChannel fc;
    File pipe;
    MappedByteBuffer mbb;
    OutputStream os;
    Random rand = new Random();

    private boolean serverError = false;
    private int openedStreams = 0;
    private Set<SleuthkitClientInputStream> currentStreams = new HashSet<>();
    private int priority = 0;
    private long requestTime = 0;

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

    synchronized boolean isServerError() {
        return serverError;
    }

    synchronized void setServerError(boolean error) {
        serverError = error;
    }

    private synchronized void checkTimeout() {
        if (requestTime == 0)
            return;
        if (SleuthkitServer.getByte(mbb, 0) != FLAGS.SQLITE_READ) {
            logger.info("Waiting SleuthkitServer {} database read...", id); //$NON-NLS-1$
            return;
        }
        if (System.currentTimeMillis() / 1000 - requestTime >= TIMEOUT_SECONDS) {
            logger.error("Timeout waiting SleuthkitServer " + id + " response! Restarting...");
            serverError = true;
            requestTime = 0;
            finishProcess(false);
        }
    }

    synchronized void enableTimeoutCheck(boolean enable) {
        if (enable)
            requestTime = System.currentTimeMillis() / 1000;
        else
            requestTime = 0;
    }

    public static synchronized void addEnvVar(String key, String value) {
        newEnvVars.put(key, value);
    }

    public static SleuthkitClient get() {

        synchronized (lock) {
            SleuthkitClient sc = clientPriorityQueue.poll();
            sc.priority++;
            clientPriorityQueue.add(sc);
            return sc;
        }

    }

    public static void initSleuthkitServers(File tskDB) throws InterruptedException {
        if (initSleuthkitServers.get()) {
            return;
        }
        FileSystemConfig fsConfig = ConfigurationManager.get().findObject(FileSystemConfig.class);
        if (tskDB.exists() && fsConfig.isRobustImageReading()) {
            synchronized (SleuthkitClient.class) {
                if (!initSleuthkitServers.get()) {
                    SleuthkitClient.initSleuthkitServers0(tskDB);
                    initSleuthkitServers.set(true);
                }
            }
        }
    }

    private static void initSleuthkitServers0(File DB) throws InterruptedException {
        tskDb = DB;
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
            sc.finishProcess(true);
    }

    private SleuthkitClient() {
        while (process == null || !isAlive(process)) {
            start();
        }
    }

    private synchronized void start() {

        LocalConfig localConfig = ConfigurationManager.get().findObject(LocalConfig.class);
        String pipePath = localConfig.getIndexerTemp() + "/pipe-" + id; //$NON-NLS-1$

        String classpath = Configuration.getInstance().appRoot + "/lib/*"; //$NON-NLS-1$
        PluginConfig pluginConfig = ConfigurationManager.get().findObject(PluginConfig.class);
        if (pluginConfig.getTskJarFile() != null) {
            classpath += SystemUtils.IS_OS_WINDOWS ? ";" : ":";
            classpath += pluginConfig.getTskJarFile().getAbsolutePath();
        }

        String[] cmd = { "java", "-cp", classpath, "-Xmx128M", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                "-Djava.io.tmpdir=" + System.getProperty("java.io.tmpdir").replace("\"", "\\\"") + File.separator + "tsk-server-" + Math.abs(rand.nextLong()),
                SleuthkitServer.class.getCanonicalName(), tskDb.getAbsolutePath(), // $NON-NLS-1$
                Configuration.getInstance().appRoot, pipePath };

        try {
            logger.info("Starting SleuthkitServer " + id + ": " + Arrays.asList(cmd));

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.environment().putAll(newEnvVars);
            process = pb.start();

            logStdErr(process.getErrorStream(), id);

            is = process.getInputStream();
            os = process.getOutputStream();

            int size = SleuthkitServer.MMAP_FILE_SIZE;
            pipe = new File(pipePath);
            try (RandomAccessFile raf = new RandomAccessFile(pipePath, "rw")) { //$NON-NLS-1$
                raf.setLength(size);
                fc = raf.getChannel();
                mbb = fc.map(MapMode.READ_WRITE, 0, size);
                mbb.load();
            } catch (ClosedByInterruptException e) {
                // clear interrupt status
                Thread.interrupted();
                throw e;
            }

            is.read();
            boolean ok = false;
            while (!(ok = SleuthkitServer.getByte(mbb, 0) == FLAGS.DONE)
                    && SleuthkitServer.getByte(mbb, 0) != FLAGS.ERROR) {
                Thread.sleep(1);
            }

            if (!ok) {
                throw new Exception("Error starting SleuthkitServer " + id); //$NON-NLS-1$
            }

            logger.info("Starting SleuthkitServer {} started.", id);

        } catch (Exception e) {
            e.printStackTrace();
            finishProcess(false);
        }
    }

    private static void logStdErr(final InputStream is, final int id) {
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

    private synchronized boolean ping() {
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

    private synchronized boolean isFineToUse() {
        if (serverError) {
            return false;
        }
        if (!ping()) {
            logger.warn("Ping SleuthkitServer " + this.id + " failed! Restarting..."); //$NON-NLS-1$ //$NON-NLS-2$
            return false;
        }
        if (openedStreams > MAX_STREAMS && currentStreams.size() == 0) {
            logger.info("Restarting SleuthkitServer {} to clean possible resource leaks.", id); //$NON-NLS-1$
            return false;
        }
        return true;
    }

    public synchronized SeekableInputStream getInputStream(int id, String path) throws IOException {

        if (!isFineToUse()) {
            restartServer();
        }

        SleuthkitClientInputStream stream = new SleuthkitClientInputStream(id, path, this);
        currentStreams.add(stream);
        openedStreams++;
        return stream;
    }

    synchronized void restartServer() throws IOException {

        finishProcess(false);

        while (process == null || !isAlive(process)) {
            start();
        }

        openedStreams = 0;
        currentStreams.forEach(s -> s.seekAfterRestart = true);
        serverError = false;
    }

    synchronized void removeStream(SleuthkitClientInputStream stream) {
        boolean removed = currentStreams.remove(stream);
        if (removed) {
            synchronized (lock) {
                clientPriorityQueue.remove(this);
                priority--;
                clientPriorityQueue.add(this);
            }
        }
    }

    private synchronized void finishProcess(boolean deletemmapFile) {
        if (process != null) {
            process.destroyForcibly();
            try {
                process.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            process = null;
        }
        try {
            if (fc != null) {
                fc.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        fc = null;
        mbb = null;

        if (deletemmapFile) {
            int tries = 10;
            do {
                System.gc();
                logger.info("Trying to delete " + pipe.getAbsolutePath());
            } while (!pipe.delete() && tries-- > 0);
        }
    }

    private static boolean isAlive(Process p) {
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
