package dpf.sp.gpinf.indexer.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.datasource.SleuthkitReader;
import dpf.sp.gpinf.indexer.util.SleuthkitServer.FLAGS;

public class SleuthkitClient {

  static Logger logger = LoggerFactory.getLogger(SleuthkitServer.class);
  
  private static final int START_PORT = 2000;
  private static final int MAX_PORT = 65535;

  static volatile String dbDirPath;
  static AtomicInteger portStart = new AtomicInteger(START_PORT);

  Process process;
  Socket socket;
  InputStream is;
  //MappedBusReader reader;
  FileChannel fc;
  MappedByteBuffer out;
  OutputStream os;

  volatile boolean serverError = false;

  private static final int max_streams = 10000;
  private int openedStreams = 0;
  private Set<Long> currentStreams = new HashSet<Long>();

  /*private static final ThreadLocal<SleuthkitClient> threadLocal =
   new ThreadLocal<SleuthkitClient>() {
   @Override protected SleuthkitClient initialValue() {
   return new SleuthkitClient();
   }
   };*/
  private static ConcurrentHashMap<String, SleuthkitClient> clients = new ConcurrentHashMap<String, SleuthkitClient>();

  public static SleuthkitClient get(String dbPath) {
      return get(Thread.currentThread().getThreadGroup(), dbPath);
  }
  
  public static SleuthkitClient get(ThreadGroup tg, String dbPath) {
    dbDirPath = dbPath;
    String threadGroupName = tg.getName();
    SleuthkitClient sc = clients.get(threadGroupName);
    if (sc == null) {
      sc = new SleuthkitClient();
      clients.put(threadGroupName, sc);
    }
    return sc;
  }

  private SleuthkitClient() {
      start();
  }

  private void start() {

    int port = portStart.getAndIncrement();
    
    if(port > MAX_PORT) {
    	port = START_PORT;
    	portStart.set(port + 1);
    }

    String pipePath = Configuration.indexerTemp + "/pipe-" + port; //$NON-NLS-1$
    
    String classpath = Configuration.appRoot + "/iped.jar"; //$NON-NLS-1$
    if(Configuration.tskJarFile != null) {
        if(System.getProperty("os.name").toLowerCase().startsWith("windows")) //$NON-NLS-1$ //$NON-NLS-2$
            classpath += ";";
        else
            classpath += ":";
        classpath += Configuration.tskJarFile.getAbsolutePath(); //$NON-NLS-1$
    }

    String[] cmd = {"java", "-cp", classpath, "-Xmx128M", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    SleuthkitServer.class.getCanonicalName(), dbDirPath + "/" + SleuthkitReader.DB_NAME, String.valueOf(port), pipePath}; //$NON-NLS-1$

    try {
      ProcessBuilder pb = new ProcessBuilder(cmd);
      process = pb.start();
      finishProcessOnJVMShutdown(process);
      
      logStdErr(process.getInputStream(), port);
      logStdErr(process.getErrorStream(), port);

      Thread.sleep(2000);
      
      //is = process.getInputStream();
      //os = process.getOutputStream();

      socket = new Socket();
      socket.setPerformancePreferences(0, 1, 2);
      socket.setReceiveBufferSize(1);
      socket.setSendBufferSize(1);
      socket.setTcpNoDelay(true);
      socket.connect(new InetSocketAddress("127.0.0.1", port)); //$NON-NLS-1$
      socket.setSoTimeout(60000);
      is = socket.getInputStream();
      os = socket.getOutputStream();

      int size = 10 * 1024 * 1024;
      RandomAccessFile raf = new RandomAccessFile(pipePath, "rw"); //$NON-NLS-1$
      raf.setLength(size);
      fc = raf.getChannel();
      out = fc.map(MapMode.READ_WRITE, 0, size);
      out.load();

      is.read();
      boolean ok = false;
      while (!(ok = SleuthkitServer.getByte(out, 0) == FLAGS.DONE) && SleuthkitServer.getByte(out, 0) != FLAGS.ERROR) {
        Thread.sleep(1);
      }

      if (!ok) {
        throw new Exception("Error starting SleuthkitServer"); //$NON-NLS-1$
      }

    } catch (Exception e) {
      //e.printStackTrace();
      if (process != null) {
        process.destroy();
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
        	if(!msg.isEmpty())
        		logger.info("SleuthkitServer port" + port + ": " + msg); //$NON-NLS-1$ //$NON-NLS-2$
          }

        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }.start();
  }

  public synchronized SeekableInputStream getInputStream(int id, String path) throws IOException {

    if (serverError || (openedStreams > max_streams && currentStreams.size() == 0)) {
      if (process != null) {
        process.destroy();
      }
      process = null;
      if (!serverError) 
        logger.info("Restarting SleuthkitServer to clean possible resource leaks."); //$NON-NLS-1$
      serverError = false;
      openedStreams = 0;
      currentStreams.clear();
    }

    while (process == null || !isAlive(process)) {
      start();
    }

    SleuthkitClientInputStream stream = new SleuthkitClientInputStream(id, path, this);

    currentStreams.add(stream.streamId);
    openedStreams++;

    return stream;
  }

  synchronized void removeStream(long streamID) {
    currentStreams.remove(streamID);
  }

  private void finishProcessOnJVMShutdown(final Process p) {
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        p.destroy();
      }
    });
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
