package dpf.sp.gpinf.indexer.parsers;



import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import junit.framework.TestCase;

public class ImageOCRMetadataParserTest extends TestCase{
    
    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }
    
    @Test
    public void testImageOCRMetadataParsingJPEG() throws IOException, SAXException, TikaException{

        ImageOCRMetadataParser parser = new ImageOCRMetadataParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        InputStream stream = getStream("test-files/test_lenajpeg.jpg");
        metadata.set(Metadata.CONTENT_TYPE, "image/jpeg");
        parser.parse(stream, handler, metadata, context);
        //tiff
        assertEquals("512", metadata.get(Metadata.IMAGE_LENGTH));
        assertEquals("512", metadata.get(Metadata.IMAGE_WIDTH));
        assertEquals("8", metadata.get(Metadata.BITS_PER_SAMPLE));
        assertEquals("Google", metadata.get(Metadata.SOFTWARE));
        
    }
    @Test
    public void testImageOCRMetadataParsingPNG() throws IOException, SAXException, TikaException{

        ImageOCRMetadataParser parser = new ImageOCRMetadataParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new DefaultHandler();
        ParseContext context = new ParseContext();
        InputStream stream = getStream("test-files/test_lenapng.png");
        metadata.set(Metadata.CONTENT_TYPE, "image/png");
        //tiff
        parser.parse(stream, handler, metadata, context);
        assertEquals("512", metadata.get(Metadata.IMAGE_LENGTH));
        assertEquals("512", metadata.get(Metadata.IMAGE_WIDTH));
        assertEquals("8 8 8", metadata.get(Metadata.BITS_PER_SAMPLE));
        
    }
    
    @Test
    public void testImageOCRMetadataParsingTIFF() throws IOException, SAXException, TikaException{

        ImageOCRMetadataParser parser = new ImageOCRMetadataParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        InputStream stream = getStream("test-files/test_lenatiff.tiff");
        metadata.set(Metadata.CONTENT_TYPE, "image/tiff");
        parser.parse(stream, handler, metadata, context);
        assertEquals("512", metadata.get(Metadata.IMAGE_LENGTH));
        assertEquals("512", metadata.get(Metadata.IMAGE_WIDTH));
        assertEquals("8", metadata.get(Metadata.BITS_PER_SAMPLE));
        assertEquals("3", metadata.get(Metadata.SAMPLES_PER_PIXEL));

    }
    
    @Test
    public void testImageOCRMetadataParsingBMP() throws IOException, SAXException, TikaException{

        ImageOCRMetadataParser parser = new ImageOCRMetadataParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        InputStream stream = getStream("test-files/test_lenabmp.bmp");
        metadata.set(Metadata.CONTENT_TYPE, "image/bmp");
        parser.parse(stream, handler, metadata, context);
        assertEquals("512", metadata.get(Metadata.IMAGE_LENGTH));
        assertEquals("512", metadata.get(Metadata.IMAGE_WIDTH));
        assertEquals("8 8 8 8", metadata.get(Metadata.BITS_PER_SAMPLE));

    }  
    
    @Test
    public void testImageOCRMetadataParsingJP2() throws IOException, SAXException, TikaException{

        ImageOCRMetadataParser parser = new ImageOCRMetadataParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        InputStream stream = getStream("test-files/test_lenajp2.jp2");
        metadata.set(Metadata.CONTENT_TYPE, "image/jp2");
        parser.parse(stream, handler, metadata, context);
        
    }  
    
    @Test
    public void testImageOCRMetadataParsingGIF() throws IOException, SAXException, TikaException{

        ImageOCRMetadataParser parser = new ImageOCRMetadataParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        InputStream stream = getStream("test-files/test_horsegif.gif");
        metadata.set(Metadata.CONTENT_TYPE, "image/gif");
        parser.parse(stream, handler, metadata, context);
        assertEquals("342", metadata.get(Metadata.IMAGE_LENGTH));
        assertEquals("500", metadata.get(Metadata.IMAGE_WIDTH));

    }  
}
