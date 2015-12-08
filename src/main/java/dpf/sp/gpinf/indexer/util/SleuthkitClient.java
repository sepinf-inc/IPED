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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.datasource.SleuthkitReader;
import dpf.sp.gpinf.indexer.util.SleuthkitServer.FLAGS;

public class SleuthkitClient {

	static Logger logger = LoggerFactory.getLogger(SleuthkitServer.class);
	
	static volatile String dbDirPath;
	static AtomicInteger portStart = new AtomicInteger(43522);
	
	Process process;
	Socket socket;
	InputStream is;
	//MappedBusReader reader;
	FileChannel fc;
	MappedByteBuffer out;
	OutputStream os;
	
    /*private static final ThreadLocal<SleuthkitClient> threadLocal =
        new ThreadLocal<SleuthkitClient>() {
            @Override protected SleuthkitClient initialValue() {
            	return new SleuthkitClient();
        }
    };*/
    
    private static ConcurrentHashMap<String, SleuthkitClient> clients = new ConcurrentHashMap<String, SleuthkitClient>();
	
	public static SleuthkitClient get(String dbPath){
		dbDirPath = dbPath;
		String threadGroupName = Thread.currentThread().getThreadGroup().getName();
		SleuthkitClient sc = clients.get(threadGroupName);
		if(sc == null){
		    sc = new SleuthkitClient();
		    clients.put(threadGroupName, sc);
		}
		return sc;
		//return threadLocal.get();
	}
	
	private SleuthkitClient(){
	}
	
	private void start(){
		
		int port = portStart.getAndIncrement();
		
		String pipePath = Configuration.indexerTemp + "/pipe-" + port;
		
		String[] cmd = {"java", "-cp", Configuration.configPath + "/iped.jar", "-Xmx128M", 
				SleuthkitServer.class.getCanonicalName(), dbDirPath + "/" + SleuthkitReader.DB_NAME, String.valueOf(port), pipePath};
		
		try {
			ProcessBuilder pb = new ProcessBuilder(cmd);
			process = pb.start();
			finishProcessOnJVMShutdown(process);
			
			is = process.getInputStream();
			os = process.getOutputStream();
			
			Thread.sleep(2000);
			
			socket = new Socket();
			socket.setPerformancePreferences(0, 1, 2);
			socket.setReceiveBufferSize(1);
			socket.setSendBufferSize(1);
			socket.setTcpNoDelay(true);
			socket.connect(new InetSocketAddress("127.0.0.1", port));
			socket.setSoTimeout(60000);
			is = socket.getInputStream();
			os = socket.getOutputStream();
			
			int size = 10 *1024 *1024;
			RandomAccessFile raf = new RandomAccessFile(pipePath, "rw");
			raf.setLength(size);
			fc = raf.getChannel();
			out = fc.map(MapMode.READ_WRITE, 0, size);
			out.load();
			
			is.read();
			boolean ok = false;
			while(!(ok = out.get(0) == FLAGS.DONE) && out.get(0) != FLAGS.ERROR)
				Thread.sleep(1);
			
			if(!ok)
				throw new Exception("Error starting SleuthkitServer");
			
			new Thread(){
				public void run(){
					InputStream err = process.getErrorStream();
					byte[] b = new byte[1024 * 1024];
					try {
						int r = 0;
						while((r = err.read(b)) != -1){
							logger.info(new String(b, 0, r));
							//Thread.sleep(0, 1);
						}
						
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}.start();
			
		} catch (Exception e) {
			e.printStackTrace();
			if(process != null)
				process.destroy();
		}
	}
	
	public SeekableInputStream getInputStream(int id, String path) throws IOException{
	    
	    while(process == null || !isAlive(process))
	        start();
	        
		return new SleuthkitClientInputStream(id, path, this);
	}
	
	private void finishProcessOnJVMShutdown(final Process p){
	    Runtime.getRuntime().addShutdownHook(new Thread() {
	        @Override
	        public void run() {
	          p.destroy();
	        }
	      });
	}
	
	private boolean isAlive(Process p){
	    try{
	        p.exitValue();
	        return false;
	    }catch(Exception e){
	        return true;
	    }
	}
}
