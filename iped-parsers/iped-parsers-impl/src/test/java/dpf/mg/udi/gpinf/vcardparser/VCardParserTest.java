package dpf.mg.udi.gpinf.vcardparser;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import junit.framework.TestCase;

public class VCardParserTest extends TestCase{

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 
    
    @Test
    public void testVCardSimpleParser() throws IOException, SAXException, TikaException{

        VCardParser parser = new VCardParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_contacts.vcf");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        
        String hts = handler.toString();
        String mts = metadata.toString();

        assertTrue(hts.contains("Internet"));
        assertTrue(hts.contains("guilhermeandreuce@gmail.com"));
        assertTrue(mts.contains("emailAddress=guilhermeandreuce@gmail.com"));
        
    }
    
    @Test
    public void testVCardMultipleParser() throws IOException, SAXException, TikaException{

        VCardParser parser = new VCardParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_contactsMultiple.vcf");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        
        String hts = handler.toString();
        String mts = metadata.toString();
        

        assertTrue(hts.contains("Family: Flamingo"));
        assertTrue(hts.contains("Given: Sushi"));
        assertTrue(hts.contains("Additional"));
        assertTrue(hts.contains("myContacts"));
        assertTrue(hts.contains("021 (61)3468-2000"));
        assertTrue(hts.contains("99592-3794"));
        assertTrue(mts.contains("phoneNumber=021 (61)3468-2000"));
        assertTrue(mts.contains("phoneNumber=021 (61)3011-7666"));
        assertTrue(mts.contains("phoneNumber=99592-3794"));
        assertTrue(mts.contains("userName=Taxi Lago Norte"));
        assertTrue(mts.contains("userName=Sushi Loko Flamingo"));
        assertTrue(mts.contains("userName=Smart Assistência Técnica"));
        
        
    }
    
//    @Test
//    public void testVCardCompleteInfoParser() throws IOException, SAXException, TikaException{
//
//        VCardParser parser = new VCardParser();
//        Metadata metadata = new Metadata();
//        ContentHandler handler = new BodyContentHandler();
//        InputStream stream = getStream("test-files/test_contactsCompleteInfo.vcf");
//        ParseContext context = new ParseContext();
//        parser.getSupportedTypes(context);
//        parser.parse(stream, handler, metadata, context);
//        
//        String hts = handler.toString();
//        String mts = metadata.toString();
//        assertTrue(hts.contains("ratuxo")); 
//        assertTrue(hts.contains("Outubro 24, 1996")); 
//        assertTrue(hts.contains("nicolecity,")); 
//        assertTrue(hts.contains("Test@paraparaparapara")); 
//        assertTrue(hts.contains("www.rato.com.br")); 
//        assertTrue(hts.contains("gosta muito de dançar para-pararato rato rato: esse cara é o rato")); 
//        
//
//        assertTrue(mts.contains("userAddress=R. da Casa do Prof. Elder isso mesmo Bela Vista MS BR ZIP 79260-000")); 
//        assertTrue(mts.contains("userUrls=www.rato.com.br")); 
//        assertTrue(mts.contains("emailAddress=insuportavel@test.paraparapara")); 
//        assertTrue(mts.contains("phoneNumber=021 (61)3468-2000")); 
//        assertTrue(mts.contains("phoneNumber=021 (61)3011-7666")); 
//        assertTrue(mts.contains("userOrganization=[nicolecity, dança]")); 
//    }    
    
    @Test
    public void testVCardCompleteWithoutThumbParser() throws IOException, SAXException, TikaException{

        VCardParser parser = new VCardParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_contactsCompleteWithoutThumb.vcf");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        
        String hts = handler.toString();
        String mts = metadata.toString();
        
        assertTrue(hts.contains("Magnanimo Test@paraparaparapara el raton para el"));
        assertTrue(hts.contains("ratuxo"));
        assertTrue(hts.contains("myContacts"));
        assertTrue(hts.contains("R. da Casa do Prof. Elder"));
        assertTrue(hts.contains("insuportavel@test.paraparapara"));
        assertTrue(hts.contains("Magnanimo"));
        assertTrue(hts.contains("nicolecity"));
        assertTrue(hts.contains("CEO do para para"));
        assertTrue(hts.contains("gosta muito de dançar para-pararato rato rato: esse cara é o rato"));
        
        assertTrue(mts.contains("userAddress=R. da Casa do Prof. Elder isso mesmo Bela Vista MS BR ZIP 79260-000"));
        assertTrue(mts.contains("userUrls=www.rato.com.br"));
        assertTrue(mts.contains("emailAddress=insuportavel@test.paraparapara"));
        assertTrue(mts.contains("phoneNumber=99 99998-6219"));
        assertTrue(mts.contains("userOrganization=[nicolecity, dança]"));
        assertTrue(mts.contains("userName=Magnanimo Test@paraparaparapara el raton para el"));
        assertTrue(mts.contains("userNotes=gosta muito de dançar para-para"));
        assertTrue(mts.contains("rato rato rato: esse cara é o rato"));
        
        
    }    
 
    @Test
    public void testVCardCompleteHTMLToString() throws IOException, SAXException, TikaException{

        VCardParser parser = new VCardParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_contactsCompleteWithoutThumbHTMLToString.vcf");
        ParseContext context = new ParseContext();
        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("test-files/test_contactsCompleteWithoutThumbHTMLToString.vcf").getFile());
        PrintWriter out = new PrintWriter(file);
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        VCardParser.printHtmlFromString(out, stream.toString());
    }

}
