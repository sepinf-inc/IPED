package dpf.sp.gpinf.indexer.parsers;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import java.io.IOException;
import java.io.InputStream;
import junit.framework.TestCase;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.DublinCore;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import iped3.util.ExtraProperties;
import org.junit.Test;

public class RFC822ParserTest extends TestCase {
    
    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void testSimple() throws IOException, SAXException, TikaException{
        RFC822Parser parser = new RFC822Parser();
        Metadata metadata = spy(new Metadata());
        ContentHandler handler = spy(new DefaultHandler());
        ParseContext context = new ParseContext();
        InputStream stream = getStream("test-files/test_rfc822");
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        
        verify(handler).startDocument();
        verify(handler, never()).startElement(eq(XHTMLContentHandler.XHTML), eq("div"), eq("div"), any(Attributes.class));
        verify(handler, never()).endElement(XHTMLContentHandler.XHTML, "div", "div");
        verify(handler).endDocument();
        assertEquals("Guilherme Andreuce <guilhermeandreuce@gmail.com>", metadata.get(TikaCoreProperties.CREATOR));
        assertEquals("[Test] Commented: Testing RFC822 parsing", metadata.get(ExtraProperties.MESSAGE_SUBJECT));
        assertEquals("[ https://test.test.org/test/test/test-test?page=com.test.test.test.test.test:test-test&"
                + "test=test#test ] Guilherme Andreuce com(...)", metadata.get(ExtraProperties.MESSAGE_BODY));
        assertEquals("2021-04-12T08:25:34Z", metadata.get(ExtraProperties.MESSAGE_DATE));
        assertEquals("Guilherme Andreuce <guilhermeandreuce@gmail.com>", metadata.get(Metadata.AUTHOR));
        assertEquals("test@test.pf.gov", metadata.get(Metadata.MESSAGE_TO));
       
    }

    @Test
    public void testMultipart() throws IOException, SAXException, TikaException {
        RFC822Parser parser = new RFC822Parser();
        Metadata metadata = new Metadata();
        InputStream stream = getStream("test-files/test_rfc822Multipart");
        ContentHandler handler = mock(XHTMLContentHandler.class);
        parser.parse(stream, handler, metadata, new ParseContext());
        verify(handler).startDocument();
        verify(handler).endDocument();

        
        //repeat, this time looking at content
        parser = new RFC822Parser();
        metadata = new Metadata();
        stream = getStream("test-files/test_rfc822Multipart");
        handler = new BodyContentHandler();
        parser.parse(stream, handler, metadata, new ParseContext());
        
        //tests correct decoding of quoted printable text, including UTF-8 bytes into Unicode
        String bodyText = handler.toString();
        assertTrue(bodyText.contains("logo.gif"));

    }
    
    @Test
    public void testQuotedPrintable() throws IOException, SAXException, TikaException {
        RFC822Parser parser = new RFC822Parser();
        Metadata metadata = new Metadata();
        InputStream stream = getStream("test-files/test_rfc822Quoted");
        ParseContext context = new ParseContext();
        ContentHandler handler = new BodyContentHandler();
        parser.parse(stream, handler, metadata, context);
        stream.close();
        //tests correct decoding of quoted printable text, including UTF-8 bytes into Unicode
        String bodyText = metadata.get(ExtraProperties.MESSAGE_BODY).toString();
        assertTrue(bodyText.contains("Düsseldorf has non-ascii."));
        assertTrue(bodyText.contains("Lines can be split like this."));
        assertFalse(bodyText.contains("=")); //there should be no escape sequences

    }

    @Test
    public void testBase64() throws IOException, SAXException, TikaException{
        RFC822Parser parser = new RFC822Parser();
        Metadata metadata = new Metadata();
        InputStream stream = getStream("test-files/test_rfc822Base64");
        ContentHandler handler = new BodyContentHandler();
        parser.parse(stream, handler, metadata, new ParseContext());
        //tests correct decoding of base64 text, including ISO-8859-1 bytes into Unicode
        String bodyText = metadata.get(ExtraProperties.MESSAGE_BODY).toString();
        assertTrue(bodyText.contains("Here is some text, with international characters, voil\u00E0!"));

    }
    
    @Test
    public void testI18NHeaders() throws IOException, SAXException, TikaException {
        RFC822Parser parser = new RFC822Parser();
        Metadata metadata = new Metadata();
        InputStream stream = getStream("test-files/test_rfc822I18NHeaders");
        ContentHandler handler = new BodyContentHandler();


            parser.parse(stream, handler, metadata, new ParseContext());
            assertEquals("Keld J\u00F8rn Simonsen <keld@dkuug.dk>", 
                    metadata.get(TikaCoreProperties.CREATOR));
            assertEquals("If you can read this you understand the example.", 
                    metadata.get(ExtraProperties.MESSAGE_SUBJECT));
    }

    @Test
    public void testUnusualFromAddress() throws Exception {
       RFC822Parser parser = new RFC822Parser();
       Metadata metadata = new Metadata();
       InputStream stream = getStream("test-files/test_rfc822OddFrom");
       ContentHandler handler = new BodyContentHandler();

       parser.parse(stream, handler, metadata, new ParseContext());
       assertEquals("Saved by Windows Internet Explorer 7", 
               metadata.get(TikaCoreProperties.CREATOR));
       assertEquals("Air Permit Programs | Air & Radiation | US EPA", 
               metadata.get(TikaCoreProperties.TITLE));
       assertEquals("Air Permit Programs | Air & Radiation | US EPA", 
               metadata.get(ExtraProperties.MESSAGE_SUBJECT));
    }




    @Test
    public void testSomeMissingHeaders() throws Exception {
       RFC822Parser parser = new RFC822Parser();
       Metadata metadata = new Metadata();
       InputStream stream = getStream("test-files/test_rfc822LimitedHeaders");
       ContentHandler handler = new BodyContentHandler();

       parser.parse(stream, handler, metadata, new ParseContext());
       assertEquals(true, metadata.isMultiValued(TikaCoreProperties.CREATOR));
       assertEquals("xyz", metadata.getValues(TikaCoreProperties.CREATOR)[0]);
       assertEquals("abc", metadata.getValues(TikaCoreProperties.CREATOR)[1]);
       assertEquals(true, metadata.isMultiValued(Metadata.MESSAGE_FROM));
       assertEquals("xyz", metadata.getValues(Metadata.MESSAGE_FROM)[0]);
       assertEquals("abc", metadata.getValues(Metadata.MESSAGE_FROM)[1]);
       assertEquals(true, metadata.isMultiValued(Metadata.MESSAGE_TO));
       assertEquals("abc", metadata.getValues(Metadata.MESSAGE_TO)[0]);
       assertEquals("def", metadata.getValues(Metadata.MESSAGE_TO)[1]);
       assertEquals("abcd", metadata.get(DublinCore.TITLE));
       assertEquals("abcd", metadata.get(ExtraProperties.MESSAGE_SUBJECT));
    }



}
