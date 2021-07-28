package dpf.sp.gpinf.indexer.parsers;

import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.lang.StringUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaMetadataKeys;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import junit.framework.TestCase;

public class MSAccessParserTest extends TestCase{

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }
    @Test
    public void testMboxParsing() throws IOException, SAXException, TikaException{

            MSAccessParser parser = new MSAccessParser();
            Metadata metadata = new Metadata();
            ContentHandler handler = new DefaultHandler();
            ParseContext context = new ParseContext();
            InputStream stream = getStream("test-files/test_mdb.mdb");
            parser.getSupportedTypes(context);
            parser.parse(stream, handler, metadata, context);


    }
    
    @SuppressWarnings({ "deprecation", "static-access" })
    @Test
    public void testMSAccessMetadata() throws IOException, SAXException, TikaException{
            
            String filepath = "test-files/test_mdb.mdb";
            MSAccessParser parser = new MSAccessParser();
            Metadata metadata = new Metadata();
            BodyContentHandler handler = new BodyContentHandler();
            ParseContext context = new ParseContext();
            InputStream stream = getStream(filepath);
            metadata.add(TikaMetadataKeys.RESOURCE_NAME_KEY, filepath);
            context.set(Parser.class, parser);
            parser.parse(stream, handler, metadata, context);

            
            assertEquals("Arial Software", metadata.get(metadata.COMPANY));
            assertEquals("Arial Software", metadata.get(metadata.AUTHOR));
            assertEquals("application/x-msaccess", metadata.get(metadata.CONTENT_TYPE));
            assertEquals("Campaign_Template", metadata.get(StringUtils.capitalize(metadata.TITLE)));
    }
    
    @Test
    public void testMSAccessHandler() throws IOException, SAXException, TikaException{
            
            String filepath = "test-files/test_mdb.mdb";
            MSAccessParser parser = new MSAccessParser();
            Metadata metadata = new Metadata();
            BodyContentHandler handler = new BodyContentHandler();
            ParseContext context = new ParseContext();
            InputStream stream = getStream(filepath);
            context.set(Parser.class, parser);
            parser.parse(stream, handler, metadata, context);

            String hts = handler.toString();
            
            assertTrue(hts.contains("Email_Address"));
            assertTrue(hts.contains("test@test.pf.com"));
            assertTrue(hts.contains("gege@baba.pf.com"));
            assertTrue(hts.contains("sergiomorales@moral.com"));
            
            assertTrue(hts.contains("First_Name"));
            assertTrue(hts.contains("pftest"));
            assertTrue(hts.contains("geraldo"));
            assertTrue(hts.contains("sérgio"));
            
            assertTrue(hts.contains("Last_Name"));
            assertTrue(hts.contains("supertest"));
            assertTrue(hts.contains("barba"));
            assertTrue(hts.contains("mörales"));
    
            assertTrue(hts.contains("Address"));
            assertTrue(hts.contains("asasul"));
            assertTrue(hts.contains("praia do cabo"));
            assertTrue(hts.contains("sèrgiocitý"));
    
            assertTrue(hts.contains("City"));
            assertTrue(hts.contains("brasilia"));
            assertTrue(hts.contains("rio de fevereiro"));
            assertTrue(hts.contains("curitiba"));

            assertTrue(hts.contains("State"));
            assertTrue(hts.contains("df"));
            assertTrue(hts.contains("rf"));
            assertTrue(hts.contains("pr"));

            assertTrue(hts.contains("Country"));
            assertTrue(hts.contains("brazil"));
            assertTrue(hts.contains("uniao dos estados do brazil"));
            assertTrue(hts.contains("federação brasileira"));
    
    }    
    

}
