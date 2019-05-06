package dpf.sp.gpinf.indexer.parsers.util;

import java.io.File;
import java.io.Serializable;

public class OCROutputFolder implements Serializable{
    
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private static File staticPath;
	
	private File path;
	
	public OCROutputFolder(){
	    this.path = staticPath;
	}
	
	public OCROutputFolder(File path){
		this.path = path;
	}

	public File getPath() {
	    return path;
	}
	
	public static void setStaticPath(File path) {
	    staticPath = path;
	}

}
