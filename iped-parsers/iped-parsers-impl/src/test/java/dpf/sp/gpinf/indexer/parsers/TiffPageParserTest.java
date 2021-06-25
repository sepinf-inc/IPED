package dpf.sp.gpinf.indexer.parsers;


import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.util.ToXMLContentHandler;
import junit.framework.TestCase;

public class TiffPageParserTest extends TestCase{

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }
    
    @Test
    public void testImageOCRMetadataParsingTIFF() throws IOException, SAXException, TikaException{

        TiffPageParser parser = new TiffPageParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new ToXMLContentHandler();
        ParseContext context = new ParseContext();
        InputStream stream = getStream("test-files/test_lenaTiff.tiff");
        metadata.set(Metadata.CONTENT_TYPE, "image/tiff");
        parser.parse(stream, handler, metadata, context);
        
        String mts = metadata.toString();
        String hts = handler.toString();
        assertTrue(mts.contains("tiff:NumPages=1"));
        assertTrue(hts.contains("xmlns=\"http://www.w3.org/1999/xhtml\""));
        assertTrue(hts.contains("<meta name=\"tiff:NumPages\" content=\"1\" />"));
        assertTrue(hts.contains("<meta name=\"Content-Type\" content=\"image/tiff\" />"));

    }
}
