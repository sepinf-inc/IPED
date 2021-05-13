package dpf.inc.sepinf.browsers.parsers;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;

public class EdgeWebCacheParserTest extends AbstractPkgTest{

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 
    
    @Test
    public void testEdgeBookmarkParser() throws IOException, SAXException, TikaException{

        //This parser uses edbparser. Edbparser uses external software. 
        //I'll add this and other softwares like regripper later.
        //For now, i'll be testing parsers that need external softwares locally.
        
        EdgeWebCacheParser parser = new EdgeWebCacheParser();
        Metadata metadata = new Metadata();
        metadata.add(IndexerDefaultParser.INDEXER_CONTENT_TYPE,
                MediaType.application("x-edge-web-cache").toString());
        ContentHandler handler = new BodyContentHandler(100000000);
        InputStream stream = getStream("test-files/test_webCache.dat");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.setExtractEntries(true);
//        parser.parse(stream, handler, metadata, edgeContext);
        
        assertEquals(0, edgetracker.bookmarktitle.size());
        assertEquals(0, edgetracker.bookmarkurl.size());
        assertEquals(0, edgetracker.bookmarkcreated.size());
        assertEquals(0, edgetracker.bookmarkmodified.size());
        

        
    }
}
