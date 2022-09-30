package iped.parsers.mail;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.DublinCore;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.junit.Test;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import iped.properties.ExtraProperties;
import junit.framework.TestCase;

public class RFC822ParserTest extends TestCase {

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    @Test
    public void testSimple() throws IOException, SAXException, TikaException {
        RFC822Parser parser = new RFC822Parser();
        Metadata metadata = spy(new Metadata());
        ContentHandler handler = spy(new DefaultHandler());
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_rfc822")) {
            parser.parse(stream, handler, metadata, context);

            verify(handler).startDocument();
            verify(handler, never()).startElement(eq(XHTMLContentHandler.XHTML), eq("div"), eq("div"),
                    any(Attributes.class));
            verify(handler, never()).endElement(XHTMLContentHandler.XHTML, "div", "div");
            verify(handler).endDocument();
            assertEquals("Guilherme Andreuce <guilhermeandreuce@gmail.com>", metadata.get(TikaCoreProperties.CREATOR));
            assertEquals("[Test] Commented: Testing RFC822 parsing", metadata.get(ExtraProperties.MESSAGE_SUBJECT));
            assertEquals(
                    "[ https://test.test.org/test/test/test-test?page=com.test.test.test.test.test:test-test&"
                            + "test=test#test ] Guilherme Andreuce com(...)",
                    metadata.get(ExtraProperties.MESSAGE_BODY));
            assertEquals("2021-04-12T08:25:34Z", metadata.get(ExtraProperties.MESSAGE_DATE));
            assertEquals("Guilherme Andreuce <guilhermeandreuce@gmail.com>", metadata.get(TikaCoreProperties.CREATOR));
            assertEquals("test@test.pf.gov", metadata.get(Metadata.MESSAGE_TO));
            assertEquals("0", metadata.get(ExtraProperties.MESSAGE_ATTACHMENT_COUNT));
            assertEquals(null, metadata.get(ExtraProperties.MESSAGE_IS_ATTACHMENT));
        }

    }

    @Test
    public void testMultipart() throws IOException, SAXException, TikaException {
        RFC822Parser parser = new RFC822Parser();
        Metadata metadata = new Metadata();
        ContentHandler handler = mock(XHTMLContentHandler.class);
        try (InputStream stream = getStream("test-files/test_rfc822Multipart")) {
            parser.parse(stream, handler, metadata, new ParseContext());
            verify(handler).startDocument();
            verify(handler).endDocument();
        }
        try (InputStream stream = getStream("test-files/test_rfc822Multipart")) {

            // repeat, this time looking at content
            parser = new RFC822Parser();
            metadata = new Metadata();
            handler = new BodyContentHandler();
            parser.parse(stream, handler, metadata, new ParseContext());

            // tests correct decoding of quoted printable text, including UTF-8 bytes into
            // Unicode
            String hts = handler.toString();
            assertTrue(hts.contains("logo.gif"));
            assertEquals("DigitalPebble <julien@digitalpebble.com>", metadata.get(TikaCoreProperties.CREATOR));
            assertEquals("This is a test for parsing multi-part mails. "
                    + "With some funky HTML code an a picture attached. " + "Text specific to body 1. -- ** *(...)",
                    metadata.get(ExtraProperties.MESSAGE_BODY));
            assertEquals(null, metadata.get(ExtraProperties.MESSAGE_IS_ATTACHMENT));
            assertEquals("lists.digitalpebble@gmail.com", metadata.get(Metadata.MESSAGE_TO));
            assertEquals("1", metadata.get(ExtraProperties.MESSAGE_ATTACHMENT_COUNT));
            assertEquals("Test Multi Part Message", metadata.get(TikaCoreProperties.TITLE));
        }

    }

    @Test
    public void testQuotedPrintable() throws IOException, SAXException, TikaException {
        RFC822Parser parser = new RFC822Parser();
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        ContentHandler handler = new BodyContentHandler();
        try (InputStream stream = getStream("test-files/test_rfc822Quoted")) {
            parser.parse(stream, handler, metadata, context);

            // tests correct decoding of quoted printable text, including UTF-8 bytes into
            // Unicode
            assertEquals("Another Person <another.person@another-example.com>",
                    metadata.get(TikaCoreProperties.CREATOR));
            assertEquals("Düsseldorf has non-ascii. Lines can be split like this. Spaces at the end of a line \r\n"
                    + "must be encoded.", metadata.get(ExtraProperties.MESSAGE_BODY));
            assertEquals(null, metadata.get(ExtraProperties.MESSAGE_IS_ATTACHMENT));
            assertEquals("A. Person <a.person@example.com>", metadata.get(Metadata.MESSAGE_TO));
            assertEquals("0", metadata.get(ExtraProperties.MESSAGE_ATTACHMENT_COUNT));
            assertEquals("Sample with Quoted Printable Text", metadata.get(TikaCoreProperties.TITLE));
        }

    }

    @Test
    public void testBase64() throws IOException, SAXException, TikaException {
        RFC822Parser parser = new RFC822Parser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        try (InputStream stream = getStream("test-files/test_rfc822Base64")) {
            parser.parse(stream, handler, metadata, new ParseContext());
            // tests correct decoding of base64 text, including ISO-8859-1 bytes into
            // Unicode
            String metadataMsgBody = metadata.get(ExtraProperties.MESSAGE_BODY).toString();
            assertTrue(metadataMsgBody.contains("Here is some text, with international characters, voil\u00E0!"));
        }

    }

    @Test
    public void testI18NHeaders() throws IOException, SAXException, TikaException {
        RFC822Parser parser = new RFC822Parser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        try (InputStream stream = getStream("test-files/test_rfc822I18NHeaders")) {
            parser.parse(stream, handler, metadata, new ParseContext());
            assertEquals("Keld J\u00F8rn Simonsen <keld@dkuug.dk>", metadata.get(TikaCoreProperties.CREATOR));
            assertEquals("If you can read this you understand the example.",
                    metadata.get(ExtraProperties.MESSAGE_SUBJECT));
        }
    }

    @Test
    public void testUnusualFromAddress() throws Exception {
        RFC822Parser parser = new RFC822Parser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        try (InputStream stream = getStream("test-files/test_rfc822OddFrom")) {
            parser.parse(stream, handler, metadata, new ParseContext());
            assertEquals("Saved by Windows Internet Explorer 7", metadata.get(TikaCoreProperties.CREATOR));
            assertEquals("Air Permit Programs | Air & Radiation | US EPA", metadata.get(TikaCoreProperties.TITLE));
            assertEquals("Air Permit Programs | Air & Radiation | US EPA",
                    metadata.get(ExtraProperties.MESSAGE_SUBJECT));
        }
    }

    @Test
    public void testSomeMissingHeaders() throws Exception {
        RFC822Parser parser = new RFC822Parser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        try (InputStream stream = getStream("test-files/test_rfc822LimitedHeaders")) {
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

}
