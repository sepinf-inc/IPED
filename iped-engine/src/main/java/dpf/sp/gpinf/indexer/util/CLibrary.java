package dpf.sp.gpinf.indexer.util;

import com.sun.jna.Library;

public interface CLibrary extends Library {

    int _putenv(String value);
    
    int putenv(String value);

}
