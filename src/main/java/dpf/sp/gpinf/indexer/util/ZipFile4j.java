package dpf.sp.gpinf.indexer.util;

import java.io.File;
import java.util.HashMap;

import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.FileHeader;

/**
 * Creates a map to get FileHeader from fileName more fast.
 * 
 * @author Nassif
 *
 */
public class ZipFile4j extends ZipFile{
    
    private HashMap<String, FileHeader> headersMap = new HashMap<>();

    public ZipFile4j(File zipFile) throws ZipException {
        super(zipFile);
        for(Object header : this.getFileHeaders())
            headersMap.put(((FileHeader)header).getFileName(), (FileHeader)header);
    }
    
    public FileHeader getFileHeader(String fileName) throws ZipException {
        return headersMap.get(fileName);
    }

}
