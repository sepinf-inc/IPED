package dpf.sp.gpinf.indexer.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Queue;

import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.SleuthkitCase;

import dpf.sp.gpinf.indexer.Configuration;

public class SleuthkitServer {
	
	static class FLAGS{
	    //client cmds
		static byte READ = 2;
		static byte SEEK = 3;
		static byte SIZE = 7;
        static byte POSITION = 8;
        static byte CLOSE = 9;
        //server responses
		static byte DONE = 4;
		static byte ERROR = 5;
		static byte EOF = 6;
		static byte EXCEPTION = 10;
		
		final static boolean isClientCmd(int cmd){
			return cmd != FLAGS.DONE && cmd != FLAGS.ERROR && cmd != FLAGS.EOF && cmd != FLAGS.EXCEPTION;
		}
	}
	
	static SleuthkitCase sleuthCase;
	
	static HashMap<Long, SleuthkitInputStream> sisMap = new HashMap<Long, SleuthkitInputStream>();
	
	public static void main(String args[]){
		
			String dbPath = args[0];
			String port = args[1];
			String pipePath = args[2];
			MappedByteBuffer out = null;
			try {
				
				int size = 10 *1024 *1024;
				RandomAccessFile raf = new RandomAccessFile(pipePath, "rw");
				raf.setLength(size);
				FileChannel fc = raf.getChannel();
				out = fc.map(MapMode.READ_WRITE, 0, size);
				out.load();

				InputStream in = System.in;
				OutputStream os = System.out;
				
				ServerSocket serverSocket = new ServerSocket(Integer.valueOf(port));
				serverSocket.setPerformancePreferences(0, 1, 2);
				//serverSocket.setReceiveBufferSize(1);
				Socket clientSocket = serverSocket.accept();
				clientSocket.setTcpNoDelay(true);
				//clientSocket.setSendBufferSize(1);
				//in = clientSocket.getInputStream();
				//os = clientSocket.getOutputStream();
				
				Configuration.getConfiguration(new File(dbPath).getParent() + "/indexador");
				sleuthCase = SleuthkitCase.openCase(dbPath);
				
				out.put(0, FLAGS.DONE);
				notify(os);
				
				byte[] buf = new byte[8 * 1024 * 1024];
				SleuthkitInputStream sis = null;
				
				while(true){
					try{
						byte cmd = waitCmd(out, in);
                        sis = getSis(out);
                        
                        if(cmd == FLAGS.SEEK){
                            sis.seek(out.getLong(13));
                        } 
                        else if(cmd == FLAGS.CLOSE){
                            sis = sisMap.remove(out.getLong(5));
                            sis.close();
                        }
                        else if(cmd == FLAGS.READ){
                            int len = readIn(sis, buf);
                            if(len == -1){
                                out.put(0, FLAGS.EOF);
                                notify(os);
                                continue;
                            }else
                                writeOut(out, buf, len);
                        }
                        else if(cmd == FLAGS.SIZE){
                            out.putLong(13, sis.size());
                        }
                        else if(cmd == FLAGS.POSITION){
                            out.putLong(13, sis.position());    
                        }
                        
                        out.put(0, FLAGS.DONE);
                        notify(os);
						
					} catch (Throwable e) {
						e.printStackTrace(System.err);
						byte[] msgBytes = e.getMessage().getBytes("UTF-8");
						out.putInt(13, msgBytes.length);
						out.position(17);
						out.put(msgBytes);
						out.put(0, FLAGS.EXCEPTION);
						notify(os);	
					}				
				}

			} catch (Throwable e) {
				e.printStackTrace(System.err);
				out.put(0, FLAGS.ERROR);
			}
	}
	
	private static SleuthkitInputStream getSis(MappedByteBuffer out) throws Exception{
	    long streamId = out.getLong(5);
	    SleuthkitInputStream sis = sisMap.get(streamId);
        if(sis == null){
            int id = out.getInt(1);
            Content content = sleuthCase.getAbstractFileById(id);
            if(content == null) content = sleuthCase.getContentById(id);
            sis = new SleuthkitInputStream(content);
            sisMap.put(streamId, sis);
        }
        return sis;
	}
	
	private static byte waitCmd(MappedByteBuffer out, InputStream in) throws Exception{
	    in.read();
        byte cmd;
        while(!FLAGS.isClientCmd(cmd = out.get(0)))
            Thread.sleep(1);
        return cmd;
	}
	
	private static int readIn(SleuthkitInputStream sis, byte[] buf) throws IOException{
		return sis.read(buf);
	}
	
	private static void writeOut(MappedByteBuffer out, byte[] buf, int len) throws Exception{
		out.position(17);
		out.put(buf, 0, len);
		out.putInt(13, len);
	}
	
	static void notify(OutputStream os) throws IOException{
		os.write(1);
		os.flush();
	}

}
