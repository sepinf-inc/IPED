package iped.engine.task.dvr;

import iped.io.SeekableInputStream;
import java.io.IOException;

/*

 * WFSExtractor.
 *
 * @author guilherme.dutra

*/

public class Util {


	public static long readLongFromBufLE(byte [] cbuf, int pos, int tam) {
	
		long r = 0L;
		
		for (int i = (pos-1); i > ((pos-1) - tam); i--){
			r = (r << 8) + (cbuf[i] & 0xFF);
		}
		
		return r;
	
	}

    public static int readBytesFromAbsoluteFilePos(SeekableInputStream is,byte[] cbuf, long off, long len)  throws IOException  {
				
		int r = -1;
		
        if (is != null){
		    is.seek(off);
		    r = is.readNBytes(cbuf, 0, (int) len);
        }
	
		return r;
		
	} 

    /**
     * Search the data byte array for the first occurrence 
     * of the byte array pattern.
     */
    public static int indexOf(byte[] data, byte[] pattern, int index) {
        int[] failure = computeFailure(pattern);

        int j = 0;

        for (int i = index; i < data.length; i++) {
            while (j > 0 && pattern[j] != data[i]) {
                j = failure[j - 1];
            }
            if (pattern[j] == data[i]) { 
                j++; 
            }
            if (j == pattern.length) {
                return i - pattern.length + 1;
            }
        }
        return -1;
    }

    /**
     * Computes the failure function using a boot-strapping process,
     * where the pattern is matched against itself.
     */
    private static int[] computeFailure(byte[] pattern) {
        int[] failure = new int[pattern.length];

        int j = 0;
        for (int i = 1; i < pattern.length; i++) {
            while (j>0 && pattern[j] != pattern[i]) {
                j = failure[j - 1];
            }
            if (pattern[j] == pattern[i]) {
                j++;
            }
            failure[i] = j;
        }

        return failure;
    }

}
