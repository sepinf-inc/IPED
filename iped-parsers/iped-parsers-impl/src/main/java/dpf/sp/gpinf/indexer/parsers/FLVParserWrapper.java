package dpf.sp.gpinf.indexer.parsers;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.video.FLVParser;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped3.util.ExtraProperties;

public class FLVParserWrapper extends AbstractParser {
    
    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    
    private FLVParser parser = new FLVParser();
    
    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return parser.getSupportedTypes(context);
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        
        Set<String> prevKeys = new HashSet<String>();
        Collections.addAll(prevKeys, metadata.names());
        
        Exception e = null;
        try{
            parser.parse(stream, handler, metadata, context);
            
        }catch(TikaException | IOException e1){
            e = e1;
            
        }finally{
            String[] keys = metadata.names();
            for(String key : keys){
                if(prevKeys.contains(key))
                    continue;
                String[] values = metadata.getValues(key);
                metadata.remove(key);
                for(String val : values)
                    metadata.add(ExtraProperties.VIDEO_META_PREFIX + key, val);
            }
            if(e != null)
                metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING, e.toString());
        }
        
    }

}
