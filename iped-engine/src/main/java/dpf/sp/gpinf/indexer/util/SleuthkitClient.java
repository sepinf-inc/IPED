package dpf.sp.gpinf.indexer.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
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

public class SleuthkitClient {

    private static Logger logger = LoggerFactory.getLogger(SleuthkitClient.class);
    
    private static final int START_PORT = 2000;
    private static final int MAX_PORT = 65535;
    private static final int MAX_STREAMS = 10000;
    
    private static ConcurrentLinkedDeque<SleuthkitClient> clientsDeque = new ConcurrentLinkedDeque<>();
    
    private static List<SleuthkitClient> clientsList = new ArrayList<>();
    
    public static int NUM_TSK_SERVERS;
    
    static volatile String dbDirPath;
    static AtomicInteger portStart = new AtomicInteger(START_PORT);
    
    static {
        SleuthKitConfig config = (SleuthKitConfig) ConfigurationManager.getInstance()
                .findObjects(SleuthKitConfig.class).iterator().next();
        NUM_TSK_SERVERS = config.getNumImageReaders();
    }

    Process process;
    Socket socket;
    InputStream is;
    FileChannel fc;
    File pipe;
    MappedByteBuffer out;
    OutputStream os;

    volatile boolean serverError = false;
    
    private int openedStreams = 0;
    private Set<Long> currentStreams = new HashSet<Long>();
    private volatile int numCurrentStreams = 0;
    
    public static SleuthkitClient get() {
        SleuthkitClient sc = clientsDeque.pollLast();        
        if(sc != null)
            return sc;
        
        int lessCurrentStreams = Integer.MAX_VALUE;
        SleuthkitClient lessBusyClient = null;
        for(int i = 0; i < clientsList.size(); i++) {
            SleuthkitClient currentClient = clientsList.get(i);
            if(currentClient.numCurrentStreams < lessCurrentStreams) {
                lessCurrentStreams = currentClient.numCurrentStreams;
                lessBusyClient = currentClient;
            }
        }
        return lessBusyClient;
    }

    public static void initSleuthkitServers(final String dbPath)
            throws InterruptedException {
        dbDirPath = dbPath;
        ArrayList<Thread> initThreads = new ArrayList<Thread>();
        for (int i = 0; i < NUM_TSK_SERVERS; i++) {
            Thread t = new Thread() {
                public void run() {
                    SleuthkitClient sc = new SleuthkitClient();
                    clientsDeque.add(sc);
                    synchronized(clientsList) {
                        clientsList.add(sc);
                    }
                }
            };
            t.start();
            initThreads.add(t);
        }
        for (Thread t : initThreads)
            t.join();
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

        int port = portStart.getAndIncrement();

        if (port > MAX_PORT) {
            port = START_PORT;
            portStart.set(port + 1);
        }

        LocalConfig localConfig = (LocalConfig) ConfigurationManager.getInstance().findObjects(LocalConfig.class)
                .iterator().next();
        String pipePath = localConfig.getIndexerTemp() + "/pipe-" + port; //$NON-NLS-1$

        String classpath = Configuration.getInstance().appRoot + "/lib/*"; //$NON-NLS-1$
        if (Configuration.getInstance().tskJarFile != null) {
            classpath += SystemUtils.IS_OS_WINDOWS ? ";" : ":";
            classpath += Configuration.getInstance().tskJarFile.getAbsolutePath(); // $NON-NLS-1$
        }

        String[] cmd = { "java", "-cp", classpath, "-Xmx128M", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                SleuthkitServer.class.getCanonicalName(), dbDirPath + "/" + SleuthkitReader.DB_NAME, //$NON-NLS-1$
                String.valueOf(port), pipePath };

        try {
            logger.info("Starting SleuthkitServer on port " + port + ": " + Arrays.asList(cmd));
            
            ProcessBuilder pb = new ProcessBuilder(cmd);
            process = pb.start();

            logStdErr(process.getInputStream(), port);
            logStdErr(process.getErrorStream(), port);

            Thread.sleep(5000);

            // is = process.getInputStream();
            // os = process.getOutputStream();

            socket = new Socket();
            socket.setPerformancePreferences(0, 1, 2);
            socket.setReceiveBufferSize(1);
            socket.setSendBufferSize(1);
            socket.setTcpNoDelay(true);
            socket.connect(new InetSocketAddress("127.0.0.1", port)); //$NON-NLS-1$
            socket.setSoTimeout(60000);
            is = socket.getInputStream();
            os = socket.getOutputStream();

            int size = SleuthkitServer.MMAP_FILE_SIZE;
            pipe = new File(pipePath);
            try (RandomAccessFile raf = new RandomAccessFile(pipePath, "rw")) { //$NON-NLS-1$
                raf.setLength(size);
                fc = raf.getChannel();
                out = fc.map(MapMode.READ_WRITE, 0, size);
                out.load();
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

    private void logStdErr(final InputStream is, final int port) {
        new Thread() {
            public void run() {
                byte[] b = new byte[1024 * 1024];
                try {
                    int r = 0;
                    while ((r = is.read(b)) != -1) {
                        String msg = new String(b, 0, r).trim();
                        if (!msg.isEmpty())
                            logger.info("SleuthkitServer port " + port + ": " + msg); //$NON-NLS-1$ //$NON-NLS-2$
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    public synchronized SeekableInputStream getInputStream(int id, String path) throws IOException {

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
            numCurrentStreams = 0;
        }

        while (process == null || !isAlive(process)) {
            start();
        }

        SleuthkitClientInputStream stream = new SleuthkitClientInputStream(id, path, this);

        currentStreams.add(stream.streamId);
        numCurrentStreams++;
        openedStreams++;

        return stream;
    }

    synchronized void removeStream(long streamID) {
        currentStreams.remove(streamID);
        numCurrentStreams--;
        if(numCurrentStreams == 0)
            clientsDeque.addFirst(this);
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
}
