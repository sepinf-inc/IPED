package dpf.sp.gpinf.indexer.util;

import java.io.IOException;

import iped3.io.SeekableInputStream;

public class EmptyInputStream extends SeekableInputStream{

    @Override
    public int read(byte b[]) throws IOException {
        return -1;
    }

    @Override
    public int read(byte b[], int off, int len) throws IOException {
        return -1;
    }
    
    @Override
    public int read() throws IOException{
        return -1;
    }
    
    @Override
    public int available() throws IOException{
        return 0;
    }
    
    @Override
    public long skip(long n) throws IOException{
        return 0;
        
    }
    
    @Override
    public void seek(long pos) throws IOException {
    }

    @Override
    public long position() throws IOException {
        return 0;
    }

    @Override
    public long size() throws IOException {
        return 0;
    }

}
