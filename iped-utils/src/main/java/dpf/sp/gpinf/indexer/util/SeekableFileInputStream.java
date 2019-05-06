package dpf.sp.gpinf.indexer.util;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.StandardOpenOption;

import iped3.io.SeekableInputStream;

public class SeekableFileInputStream extends SeekableInputStream{
    
    SeekableByteChannel sbc;
    long markPos, markLimit;

    public SeekableFileInputStream(File file) throws IOException {
        this.sbc = FileChannel.open(file.toPath(), StandardOpenOption.READ);
    }
    
    public SeekableFileInputStream(SeekableByteChannel channel){
        this.sbc = channel;
    }
    
    @Override
    public int read(byte b[]) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte b[], int off, int len) throws IOException {
    	ByteBuffer bb = ByteBuffer.wrap(b, off, len);
    	return sbc.read(bb);
    }
    
    @Override
    public int read() throws IOException{
       byte[] b = new byte[1];
       int i;
       do{
    	   i = read(b, 0 , 1);
       }while(i == 0);
       
       if(i == -1)
    	   return -1;
       else
    	   return b[0];
    }
    
    @Override
    public int available() throws IOException{
        long avail = sbc.size() - sbc.position();
        return (int)Math.min(avail, Integer.MAX_VALUE);
        
    }
    
    @Override
    public long skip(long n) throws IOException{
        
        long pos = sbc.position();
        long newPos = pos + n;
        long len = sbc.size();
        
        if(newPos > len)
            newPos = len;
        else if (newPos < 0)
            newPos = 0;
        
        sbc.position(newPos);
        
        return newPos - pos;
        
    }
    
    public void seek(long pos) throws IOException{
        sbc.position(pos);
    }
    
    @Override
    public boolean markSupported(){
        return true;
    }
    
    @Override
    public void mark(int mark) {
        try {
            markPos = sbc.position();
            markLimit = mark;
        } catch (IOException e) {
            markPos = 0;
            markLimit = 0;
        }
    }
    
    @Override
    public void reset() throws IOException{
        if(sbc.position() - markPos <= markLimit)
        	sbc.position(markPos);
        else
            throw new IOException("Mark limit exceeded"); //$NON-NLS-1$
    }
    
    @Override
    public void close() throws IOException{
        sbc.close();
    }

    @Override
    public long position() throws IOException {
        return sbc.position();
    }

    @Override
    public long size() throws IOException {
        return sbc.size();
    }

}
