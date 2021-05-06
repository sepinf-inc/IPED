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
import junit.framework.TestCase;

public class MultipleParserTest extends TestCase{
    
    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 
   
    @Test
    public void testMultipleParserParsingDocx() throws IOException, SAXException, TikaException{

        MultipleParser parser = new MultipleParser();
        XMLParser parserXML = new XMLParser();
        PackageParser parserPackage = new PackageParser();
        EmptyVideoParser parserEmptyVideo = new EmptyVideoParser();
        ImageOCRMetadataParser parserImageOCR = new ImageOCRMetadataParser();
        
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_mockDoc1.docx");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.addParser(parserXML);
        parser.addParser(parserPackage);
        parser.addParser(parserEmptyVideo);
        parser.addParser(parserImageOCR);
        
        parser.addSupportedTypes(parserXML.getSupportedTypes(context));
        parser.addSupportedTypes(parserPackage.getSupportedTypes(context));
        parser.parse(stream, handler, metadata, context);
        
    }
    
    @Test
    public void testMultipleParserParsingFLV() throws IOException, SAXException, TikaException{

        MultipleParser parser = new MultipleParser();
        EmptyVideoParser parserEmptyVideo = new EmptyVideoParser();
        ImageOCRMetadataParser parserImageOCR = new ImageOCRMetadataParser();
        
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_videoFlv.flv");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.addParser(parserEmptyVideo);
        parser.addParser(parserImageOCR);
        
        parser.addSupportedTypes(parserEmptyVideo.getSupportedTypes(context));
        parser.addSupportedTypes(parserImageOCR.getSupportedTypes(context));
        parser.parse(stream, handler, metadata, context);
        
    }
    
    @Test
    public void testMultipleParserParsingPNG() throws IOException, SAXException, TikaException{

        MultipleParser parser = new MultipleParser();
        EmptyVideoParser parserEmptyVideo = new EmptyVideoParser();
        ImageOCRMetadataParser parserImageOCR = new ImageOCRMetadataParser();
        
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_lenaPng.png");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.addParser(parserEmptyVideo);
        parser.addParser(parserImageOCR);
        
        parser.addSupportedTypes(parserEmptyVideo.getSupportedTypes(context));
        parser.addSupportedTypes(parserImageOCR.getSupportedTypes(context));
        parser.parse(stream, handler, metadata, context);
        
    }
    
}
