package dpf.sp.gpinf.indexer.util;

import java.io.IOException;

import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.ReadContentInputStream;

public class SleuthkitInputStream extends SeekableInputStream{
    
    ReadContentInputStream rcis;

    public SleuthkitInputStream(Content file) {
        rcis = new ReadContentInputStream(file);
    }
    
    @Override
    public int read(byte b[]) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte b[], int off, int len) throws IOException {
        return rcis.read(b, off, len);
    }
    
    @Override
    public int read() throws IOException{
        return rcis.read();
    }
    
    @Override
    public int available() throws IOException{
        return rcis.available();
    }
    
    @Override
    public long skip(long n) throws IOException{
        return rcis.skip(n);
        
    }
    
    public void seek(long pos) throws IOException{
        long newPos = rcis.seek(pos);
        if(newPos != pos)
            throw new IOException("Seek to " + pos + " failed");
    }
    
    @Override
    public boolean markSupported(){
        return rcis.markSupported();
    }
    
    @Override
    public void mark(int mark) {
        rcis.mark(mark);
    }
    
    @Override
    public void reset() throws IOException{
        rcis.reset();
    }
    
    @Override
    public void close() throws IOException{
        rcis.close();
    }

    @Override
    public long position(){
        return rcis.getCurPosition();
    }

    @Override
    public long size() {
        return rcis.getLength();
    }

}
