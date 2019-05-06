/*
 * Copyright 2012-2014, Luis Filipe da Cruz Nassif
 * 
 * This file is part of Indexador e Processador de Evidências Digitais (IPED).
 *
 * IPED is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * IPED is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with IPED.  If not, see <http://www.gnu.org/licenses/>.
 */
package dpf.sp.gpinf.indexer.util;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import net.jpountz.lz4.LZ4Compressor;
import net.jpountz.lz4.LZ4Factory;

public class RandomFilterInputStream extends FilterInputStream{
    
    static LZ4Compressor compressor;
    static int BUF_SIZE = 64 * 1024;
    static int OVERLAP = 1024;
    static int maxCompressedLen;
    
    byte[] compressed;
    byte[] buf = new byte[BUF_SIZE];
    int pos = 0, count = 0;
    
    private double compressRatioSum = 0;
    private long numCompressions = 0;
    
    static{
        LZ4Factory factory = LZ4Factory.fastestInstance();
        compressor = factory.fastCompressor();
        maxCompressedLen = compressor.maxCompressedLength(BUF_SIZE);
    }

    public RandomFilterInputStream(InputStream in) {
        super(in);
        compressed = new byte[maxCompressedLen];
    }
    
    public Double getCompressRatio(){
        if(numCompressions == 0)
            return null;
        
        return compressRatioSum / numCompressions;
    }
    
    @Override
    public int read() throws IOException{
    	if(count == -1)
    		return -1;
    	if(pos < count)
    		return buf[pos++];
    	
    	byte[] b = new byte[1];
    	int i = 0;
    	while(i == 0)
    		i = read(b);
    	if(i != -1)
    		return b[0];
    	else
    		return -1;
    }
    
    @Override
    public int read(byte b[]) throws IOException {
        return read(b, 0, b.length);
    }
    
    private final float getCompressRatio(byte[] buf, int off, int len){
        
        int compressedLength = compressor.compress(buf, off, len, compressed, 0, maxCompressedLen);
        return (float)compressedLength / len;
    }
    
    private static final boolean isRandom(float compressRatio){
        return compressRatio > 1;
    }
    
    public int read(byte b[], int off, int len) throws IOException {
        
        if(pos == count || pos == count - OVERLAP){
            if(pos == BUF_SIZE){
                System.arraycopy(buf, pos - OVERLAP, buf, 0, OVERLAP);
                pos = OVERLAP;
            }else if(pos == BUF_SIZE - OVERLAP){
            	System.arraycopy(buf, pos, buf, 0, OVERLAP);
            	pos = 0;
            }
                
            count = super.read(buf, pos, BUF_SIZE - pos);
            if(count == -1)
                return -1;
            
            count += pos;
            
            if(count >= BUF_SIZE / 2 && (pos == 0 || pos == OVERLAP)){
                float compressRatio = getCompressRatio(buf, 0, count);
                //System.out.println(compressRatio);
                compressRatioSum += compressRatio;
                numCompressions++;
                
                if(isRandom(compressRatio)){
                    pos = count - OVERLAP;
                    return 0;
                }
            }
            
        }
        if(count == -1)
            return -1;
        
        int avail = count - pos;
        int n = (avail < len) ? avail : len;
        System.arraycopy(buf, pos, b, off, n);
        
        pos += n;
        
        return n;
    }
    
    public boolean marksupported(){
        return false;
    }
    
    @Override
    public int available() throws IOException{
        int a = super.available();
        int diff = count - pos;
        
        return diff + a;
        
    }
    
    @Override
    public long skip(long n) throws IOException{
        
        int diff = count - pos;
        if(n > diff){
            long skiped = super.skip(n - diff);
            pos = count;
            return diff + skiped;
        }else{
            pos += n;
            return n;
        }
        
    }
    
    @Override
    public void mark(int mark){
        super.mark(mark);
    }
    
    @Override
    public void reset() throws IOException{
        throw new IOException("Mark/Reset not supported"); //$NON-NLS-1$
    }

}
