package dpf.sp.gpinf.indexer.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;

public class UFEDXMLWrapper extends Reader{
    
    private static final String prefix = "<value type=\"String\">";
    private static final String suffix = "</value>";
    private static final String cDataStart = "<![CDATA[";
    private static final String cDataEnd = "]]>";
    
    BufferedReader reader;
    String buffer;
    int pos = 0, size = 0;
    boolean inModel = false, inData = false, replacing = false;
    
    public UFEDXMLWrapper(File xml) throws FileNotFoundException, UnsupportedEncodingException {
        reader = new BufferedReader(new InputStreamReader(new FileInputStream(xml), "UTF-8"));
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        
        if(pos == size) {
            buffer = reader.readLine();
            if(buffer == null)
                return -1;
            
            escapeInvalidChars();
            
            buffer += "\r\n";
            pos = 0;
            size = buffer.length();
        }
        
        int min = Math.min(len, size - pos);
        System.arraycopy(buffer.toCharArray(), pos, cbuf, off, min);
        pos += min;
        
        return min;
    }
    
    private void escapeInvalidChars() {
        
        if(inModel && inData) {
            int idx = buffer.indexOf(prefix);
            if(idx != -1 && buffer.indexOf(cDataStart) != idx + prefix.length()) {
                buffer = buffer.substring(0, idx + prefix.length()) + cDataStart + buffer.substring(idx + prefix.length());
                replacing = true;
            }
            if(replacing && buffer.trim().endsWith(suffix)) {
                int i = buffer.indexOf(suffix);
                buffer = buffer.substring(0, i) + cDataEnd + buffer.substring(i);
                replacing = false;
            }
        }
        
        if(buffer.contains("<model type=\"Password\" "))
            inModel = true;
        
        if(buffer.contains("</model>"))
            inModel = false;
        
        if(buffer.contains("<field name=\"Data\" type=\"String\">"))
            inData = true;
        
        if(buffer.contains("</field>"))
            inData = false;
        
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }


}
