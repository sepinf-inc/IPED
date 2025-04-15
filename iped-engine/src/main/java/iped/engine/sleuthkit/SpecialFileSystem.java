package iped.engine.sleuthkit;

import iped.io.SeekableInputStream;
import java.util.*;
import iped.utils.IOUtil;
import iped.utils.SeekableInputStreamFactory;
import iped.engine.io.WFSInputStreamFactory;
import java.io.IOException;
import iped.engine.task.dvr.Util;

/*

 * WFSExtractor.
 *
 * @author guilherme.dutra

*/

public class SpecialFileSystem {

    private static HashMap<Long, SeekableInputStreamFactory> mapInputStreamFactory = new HashMap<Long, SeekableInputStreamFactory>();

    public static enum FSType {
        HVFS, WFS, DHFS, NONE
    }

    public synchronized static  SeekableInputStream getSeekableInputStream(SeekableInputStream sis, long tskId, String identifier) throws IOException{

        FSType fsType = getFSType(sis);        
        SeekableInputStream ret = null;
        SeekableInputStreamFactory tempSISF = null;

        switch(fsType){
            case HVFS:
                ret = sis;
                break;
            case WFS:
                tempSISF = mapInputStreamFactory.get(tskId);
                if (tempSISF != null){
                    ret = tempSISF.getSeekableInputStream(identifier);
                }else{                    
                    WFSInputStreamFactory wfsif = new WFSInputStreamFactory(sis);
                    ret = wfsif.getSeekableInputStream(identifier);
                    mapInputStreamFactory.put(tskId,wfsif);
                }
                break;
            case DHFS:
                ret = sis;
                break;                
            case NONE:
            default:
                ret = sis;
                break;
        }

        return ret;

    }

    private static FSType getFSType(SeekableInputStream fis) {


        FSType ret = FSType.NONE;

        if (fis==null)
            return ret;

        try {
            byte[] header = new byte[1 * 1024];

            //HIKVISION@HANGZHOU
            byte[] HVFS_SIG = {(byte)0x48,(byte)0x49,(byte)0x4B,(byte)0x56,(byte)0x49,(byte)0x53,(byte)0x49,(byte)0x4F,
							  (byte)0x4E,(byte)0x40,(byte)0x48,(byte)0x41,(byte)0x4E,(byte)0x47,(byte)0x5A,(byte)0x48,
							  (byte)0x4F,(byte)0x55};

            //WFS0.4 signature  
            byte[] WFS04_SIG = {(byte)0x57,(byte)0x46,(byte)0x53,(byte)0x30,(byte)0x2E,(byte)0x34};

            //WFS0.5 signature       
            byte[] WFS05_SIG = {(byte)0x57,(byte)0x46,(byte)0x53,(byte)0x30,(byte)0x2E,(byte)0x35};   

            //DHFS signature       
            byte[] DHFS_SIG = {(byte)0x44,(byte)0x48,(byte)0x46,(byte)0x53,(byte)0x34,(byte)0x2E,(byte)0x31};   

            int read = 0, off = 0;
            while (read != -1 && (off += read) < header.length) {
                read = fis.read(header, off, header.length - off);
            }

            if ( Util.indexOf(header,HVFS_SIG,0)!=-1){
                ret = FSType.HVFS;
            }
            if ( Util.indexOf(header,WFS04_SIG,0)!=-1 || Util.indexOf(header,WFS05_SIG,0)!=-1){
                ret = FSType.WFS;
            }
            if ( Util.indexOf(header,DHFS_SIG,0)!=-1){
                ret = FSType.DHFS;
            }

        } catch (Exception e) {

        } 
        return ret;
    }

    public static boolean isSpecialFileSystem(SeekableInputStream fis) {

        if (getFSType(fis)!=FSType.NONE){
            return true;
        }

        return false;

    }

}
