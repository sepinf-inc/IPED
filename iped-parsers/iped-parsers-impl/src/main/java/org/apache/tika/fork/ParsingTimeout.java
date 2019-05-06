package org.apache.tika.fork;

import java.io.Serializable;

public class ParsingTimeout implements Serializable{
    
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    
    private int parsingTimeoutMillis = 60000;
    
    public ParsingTimeout(int timeoutMillis) {
        this.parsingTimeoutMillis = timeoutMillis;
    }
    
    public int getTimeoutMillis() {
        return this.parsingTimeoutMillis;
    }
    

}
