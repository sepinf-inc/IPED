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
            InputStream stream = getStream("test-files/testMDBCampaign_Template.mdb");
            parser.getSupportedTypes(context);
            parser.parse(stream, handler, metadata, context);


    }
    
    @Test
    public void testMSAccessMetadata() throws IOException, SAXException, TikaException{
            
            String filepath = "test-files/testMDBCampaign_Template.mdb";
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
            
            String filepath = "test-files/testMDBCampaign_Template.mdb";
            MSAccessParser parser = new MSAccessParser();
            Metadata metadata = new Metadata();
            BodyContentHandler handler = new BodyContentHandler();
            ParseContext context = new ParseContext();
            InputStream stream = getStream(filepath);
            context.set(Parser.class, parser);
            parser.parse(stream, handler, metadata, context);

            String bodyText = handler.toString();
            
            assertTrue(bodyText.contains("Email_Address"));
            assertTrue(bodyText.contains("test@test.pf.com"));
            assertTrue(bodyText.contains("gege@baba.pf.com"));
            assertTrue(bodyText.contains("sergiomorales@moral.com"));
            
            assertTrue(bodyText.contains("First_Name"));
            assertTrue(bodyText.contains("pftest"));
            assertTrue(bodyText.contains("geraldo"));
            assertTrue(bodyText.contains("sérgio"));
            
            assertTrue(bodyText.contains("Last_Name"));
            assertTrue(bodyText.contains("supertest"));
            assertTrue(bodyText.contains("barba"));
            assertTrue(bodyText.contains("mörales"));
    
            assertTrue(bodyText.contains("Address"));
            assertTrue(bodyText.contains("asasul"));
            assertTrue(bodyText.contains("praia do cabo"));
            assertTrue(bodyText.contains("sèrgiocitý"));
    
            assertTrue(bodyText.contains("City"));
            assertTrue(bodyText.contains("brasilia"));
            assertTrue(bodyText.contains("rio de fevereiro"));
            assertTrue(bodyText.contains("curitiba"));

            assertTrue(bodyText.contains("State"));
            assertTrue(bodyText.contains("df"));
            assertTrue(bodyText.contains("rf"));
            assertTrue(bodyText.contains("pr"));

            assertTrue(bodyText.contains("Country"));
            assertTrue(bodyText.contains("brazil"));
            assertTrue(bodyText.contains("uniao dos estados do brazil"));
            assertTrue(bodyText.contains("federação brasileira"));
    
    }    
    

}
