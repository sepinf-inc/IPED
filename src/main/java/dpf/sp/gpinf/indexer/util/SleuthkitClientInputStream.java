package dpf.sp.gpinf.indexer.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.util.SleuthkitServer.FLAGS;

public class SleuthkitClientInputStream extends SeekableInputStream{
    
    private static Logger LOGGER = LoggerFactory.getLogger(SleuthkitClientInputStream.class);
	
    private static AtomicLong next = new AtomicLong();
    
    int sleuthId;
    String path;
    long streamId = next.getAndIncrement();
	private InputStream in;
	OutputStream os;
	int bufPos = 0;
	MappedByteBuffer mbb;
	boolean closed = false, empty = true;
	
	public SleuthkitClientInputStream(int id, String path, MappedByteBuffer mbb, InputStream in, OutputStream os){
	    this.sleuthId = id;
	    this.path = path;
		this.mbb = mbb;
		this.in = in;
		this.os = os;
	}
	
	@Override
    public int read(byte b[], int off, int len) throws IOException {
		if(closed)
			throw new IOException("Stream is closed!");
		
    	int read = readIn(b, off, len);
    	
    	return read;
    }
	
	private int readIn(byte b[], int off, int len) throws IOException {
		
		if(empty){
		    byte cmd = sendRead();
			if(cmd == FLAGS.EOF)
				return -1;
		}
		empty = false;
		
		int size = mbb.getInt(13);
		int copyLen = Math.min(len, size - bufPos);
		mbb.position(bufPos + 17);
		mbb.get(b, off, copyLen);
		bufPos += copyLen;
		if(bufPos == size)
			empty = true;
			//sendRead();
		
		return copyLen;
	}
	
	private byte sendRead() throws IOException{
	    mbb.putInt(1, sleuthId);
        mbb.putLong(5, streamId);
	    mbb.put(0, FLAGS.READ);
		
		bufPos = 0;
		empty = true;
		notifyServer();
		return waitServerResponse();
	}
	
	private byte waitServerResponse() throws IOException{
	    try {
            in.read();
        } catch (IOException e1) {
            LOGGER.error(getCrashMsg());
            throw e1;
        }
        byte cmd;
        while(FLAGS.isClientCmd(cmd = mbb.get(0)))
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                throw new InterruptedIOException(e.toString());
            }
        if (cmd == FLAGS.EXCEPTION){
            int len = mbb.getInt(13);
            byte[] b = new byte[len];
            mbb.position(17);
            mbb.get(b);
            try {
                throw new IOException("SleuthkitServer error: " + new String(b, "UTF-8"));
            } catch ( UnsupportedEncodingException e) {
            }
        }
        
	    return cmd;
	}
	
	private void notifyServer() throws IOException{
	    try {
            SleuthkitServer.notify(os);
        } catch (IOException e) {
            //LOGGER.error(getCrashMsg());
            throw e;
        }
	}
	
	private String getCrashMsg(){
	    return "Possível crash do Sleuthkit ao ler " + path;
	}

	@Override
	public void seek(long pos) throws IOException {
		if(closed)
			throw new IOException("Stream is closed!");
		
		//waitServerResponse();
		mbb.putInt(1, sleuthId);
        mbb.putLong(5, streamId);
		mbb.putLong(13, pos);
		mbb.put(0, FLAGS.SEEK);
		empty = true;
		notifyServer();
		waitServerResponse();
		
	}

	@Override
	public long position() throws IOException {
	    if(closed)
            throw new IOException("Stream is closed!");
	    
	    //waitServerResponse();
	    mbb.putInt(1, sleuthId);
        mbb.putLong(5, streamId);
	    mbb.putInt(0, FLAGS.POSITION);
	    notifyServer();
	    waitServerResponse();
        return mbb.getLong(13); 
	}

	@Override
	public long size() throws IOException {
	    if(closed)
            throw new IOException("Stream is closed!");
	    
	    //waitServerResponse();
	    mbb.putInt(1, sleuthId);
        mbb.putLong(5, streamId);
        mbb.putInt(0, FLAGS.SIZE);
        notifyServer();
        waitServerResponse();
        return mbb.getLong(13); 
	}

	@Override
	public int read() throws IOException {
		if(closed)
			throw new IOException("Stream is closed!");
		byte[] b = new byte[1];
		int i = 0;
		do{
			i = read(b);
		}while(i != 0);
		
		if(i == -1)
			return -1;
		
		return b[0];
	}
	
	public void close() throws IOException{
	    if(closed)
	        return;
		closed = true;
		//waitServerResponse();
		mbb.putInt(1, sleuthId);
        mbb.putLong(5, streamId);
		mbb.put(0, FLAGS.CLOSE);
		empty = true;
		notifyServer();
		waitServerResponse();
		
	}

}
