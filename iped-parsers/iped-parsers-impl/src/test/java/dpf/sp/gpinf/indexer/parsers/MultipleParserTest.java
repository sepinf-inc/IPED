package dpf.sp.gpinf.indexer.parsers;

import java.io.IOException;
import java.io.InputStream;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.executable.ExecutableParser;
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
        ExecutableParser exeParser = new ExecutableParser();
        RawStringParser rawParser = new RawStringParser();
        
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_arj310.exe");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.addParser(exeParser);
        parser.addParser(rawParser);
        parser.addSupportedTypes(exeParser.getSupportedTypes(context));
        parser.addSupportedTypes(rawParser.getSupportedTypes(context));
        parser.parse(stream, handler, metadata, context);
        
        String hts = handler.toString();
        String mts = metadata.toString();
        
        assertTrue(hts.contains("Compressed by Petite (c)1999 Ian Luck."));
        assertTrue(hts.contains("ExitProcess"));
        assertTrue(hts.contains("Native versions for UNIX-like operating systems."));
        assertTrue(hts.contains("This is a self-extracting archive. Run it to extract all files."));
        assertTrue(hts.contains("C:\\ARJ\\ -m -b -x"));
        assertTrue(hts.contains("ARJ32 v 3.10/Win32"));
        
        assertTrue(mts.contains("X-Parsed-By=org.apache.tika.parser.executable.ExecutableParser"));
        assertTrue(mts.contains("X-Parsed-By=dpf.sp.gpinf.indexer.parsers.RawStringParser"));
        assertTrue(mts.contains("machine:endian=Little"));
        assertTrue(mts.contains("machine:platform=Windows"));
        assertTrue(mts.contains("machine:architectureBits=32"));
        assertTrue(mts.contains("Content-Type=application/x-msdownload"));
        
    }

    
}
