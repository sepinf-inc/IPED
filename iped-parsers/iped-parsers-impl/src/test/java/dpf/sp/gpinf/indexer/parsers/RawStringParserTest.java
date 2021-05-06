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

public class RawStringParserTest extends TestCase{
    
    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 
    
    //Note: A lot of IPED parsers uses RawStringParser. It seems to be working fine. Just created this one
    //So we can test utf8 and iso8859-1 encoding properly. 
    
    @Test
    public void testRawStringUTF8() throws IOException, SAXException, TikaException{

        RawStringParser parser = new RawStringParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        InputStream stream = getStream("test-files/test_utf8");
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        String hts = handler.toString();
        assertTrue(hts.contains("issO é OUTR4 stRin8888 codificada em UTF8"));
        assertTrue(hts.contains("Essa stRin8888G esta´ sendÖOO utilizada n0 P4RSER"));
        assertTrue(hts.contains("do 1P3D para R4W STR1N85...!!111"));
    }
    
    @Test
    public void testRawStringUTF16() throws IOException, SAXException, TikaException{

        RawStringParser parser = new RawStringParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        InputStream stream = getStream("test-files/test_utf16");
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        String hts = handler.toString();
        assertTrue(hts.contains("issO é + um4 stRin8888 codificada em UTF16!!1!"));
        assertTrue(hts.contains("Essa stRin8888G esta\n sendÖOO utilizada n0 P4RSER do 1P3D para R4W STR1N85...!!111"));
        assertTrue(hts.contains("Essa pa5te está em UTF16."));
    }
    
    @Test
    public void testRawStringISO88591() throws IOException, SAXException, TikaException{

        RawStringParser parser = new RawStringParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        InputStream stream = getStream("test-files/test_iso8859-1");
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        String hts = handler.toString();
        assertTrue(hts.contains("ssO é ÜM4 stRin8888 codificada em ISO8859-1."));
        assertTrue(hts.contains("Essa stRinÝGG888 esta\n sendo utilizada n0 P4RsÈR"));
        assertTrue(hts.contains("do 1P3D para R4W STR1N85...!!111"));
    }
    
    @Test
    public void testRawStringUTF8ISO() throws IOException, SAXException, TikaException{

        RawStringParser parser = new RawStringParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        InputStream stream = getStream("test-files/test_utf8iso88591");
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        String hts = handler.toString();
        assertTrue(hts.contains("issO é OUTR4 stRin8888 codificada em UTF8."));
        assertTrue(hts.contains("Essa part3 está em UTF8.issO é ÜM4 stRin8888 codificada em ISO8859-1."));
        assertTrue(hts.contains("Essa p4rte está em ISO8859-1"));
    }
    
    @Test
    public void testRawStringUTF16ISO() throws IOException, SAXException, TikaException{

        RawStringParser parser = new RawStringParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        InputStream stream = getStream("test-files/test_utf16iso88591");
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        String hts = handler.toString();
        assertTrue(hts.contains("Essa p4rte está em ISO8859-1"));
        assertTrue(hts.contains("Essa pa5te está em UTF16."));
    }
    
    @Test
    public void testRawStringUTF8UTF16() throws IOException, SAXException, TikaException{

        RawStringParser parser = new RawStringParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        InputStream stream = getStream("test-files/test_utf8utf16");
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        String hts = handler.toString();
        assertTrue(hts.contains("Essa part3 está em UTF8.i\nssO"));
        assertTrue(hts.contains("Essa pa5te está em UTF16.")); 
        
    }
    
    @Test
    public void testRawStringUTF16ISOUTF8() throws IOException, SAXException, TikaException{

        RawStringParser parser = new RawStringParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        InputStream stream = getStream("test-files/test_utf16iso88591utf8");
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        String hts = handler.toString();
        assertTrue(hts.contains("Essa p4rte está em ISO8859-1issO"));
        assertTrue(hts.contains("Essa part3 está em UTF8."));
        assertTrue(hts.contains("Essa pa5te está em UTF16."));
    }
   
    

}

/*
# Python program for writing with particular encode
data = """sample text"""

# Change as needed (iso8859-1, utf8, utf16)
data.encode('iso8859-1')

# Writing file.
with open ('test_iso8859-1', 'w') as fp:
    fp.write(data)


# Python program for reading and merging streams

data = data2 = b''
out_data = b''

# Reading data from file1
# Change as needed
with open('test_utf16iso88591', 'rb') as fp:
    data = fp.read()
    out_data += data

# Reading data from file2
# Change as needed
with open('test_utf8', 'rb') as fp:
    data2 = fp.read()
    out_data += data2
    
# Writing file
# Change as needed    
with open ('test_utf16iso88591utf8', 'wb') as fp:
    fp.write(out_data)
*/