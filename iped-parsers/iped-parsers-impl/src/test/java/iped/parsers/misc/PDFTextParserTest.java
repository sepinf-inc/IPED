package iped.parsers.misc;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

import javax.imageio.ImageIO;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import iped.parsers.util.ComputeThumb;
import iped.properties.ExtraProperties;
import junit.framework.TestCase;

public class PDFTextParserTest extends TestCase {

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    @Test
    public void testPDFOCRTextParsing() throws IOException, SAXException, TikaException {

        PDFTextParser parser = new PDFTextParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new DefaultHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_pdfProtected.pdf")) {
            parser.parse(stream, handler, metadata, context);
        }

    }

    @Test
    public void testPDFOCRTextParsingICE() throws IOException, SAXException, TikaException {

        PDFTextParser parser = new PDFTextParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new DefaultHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.setUseIcePDF(true);
        try (InputStream stream = getStream("test-files/test_pdfProtected.pdf")) {
            parser.parse(stream, handler, metadata, context);

            assertEquals("PScript5.dll Version 5.2", metadata.getValues(TikaCoreProperties.CREATOR)[1]);
            assertEquals("Acrobat Distiller 7.0.5 (Windows)", metadata.get(TikaCoreProperties.CREATOR_TOOL));
            assertEquals("Speeches by Andrew G Haldane", metadata.get(TikaCoreProperties.SUBJECT));
            assertEquals("The Bank of England", metadata.get(TikaCoreProperties.CREATOR));
            assertEquals("April 28, 2009 10:06:56 (UTC +01:00)", metadata.get(TikaCoreProperties.CREATED));
            assertEquals(
                    "Rethinking the Financial Network, Speech by Andrew G Haldane, Executive Director, Financial Stability delivered at the Financial Student Association, Amsterdam on 28 April 2009",
                    metadata.get(TikaCoreProperties.TITLE));
            assertEquals(PDFTextParser.PDF_TYPE.toString(), metadata.get(Metadata.CONTENT_TYPE));
            assertEquals(PDFTextParser.PDF_TYPE.toString(), metadata.get(Metadata.CONTENT_TYPE));
        }

    }

    @SuppressWarnings("static-access")
    @Test
    public void testPDFOCRTextEmbbedMetadata() throws IOException, SAXException, TikaException {

        PDFTextParser parser = new PDFTextParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new DefaultHandler();
        ParseContext context = new ParseContext();
        try (InputStream stream = getStream("test-files/test_pdfProtected.pdf")) {
            parser.parse(stream, handler, metadata, context);

            assertEquals(
                    "Rethinking the Financial Network, Speech by Andrew G Haldane,"
                            + " Executive Director, Financial Stability delivered at the Financial"
                            + " Student Association, Amsterdam on 28 April 2009",
                    metadata.get(TikaCoreProperties.TITLE));
            assertEquals("Speeches by Andrew G Haldane", metadata.get(TikaCoreProperties.SUBJECT));
            assertEquals("The Bank of England", metadata.get(TikaCoreProperties.CREATOR));
            assertEquals("PScript5.dll Version 5.2", metadata.get(TikaCoreProperties.CREATOR_TOOL));
            assertEquals("Speeches by Andrew G Haldane", metadata.get(TikaCoreProperties.DESCRIPTION));
        }

    }

    @Test
    public void testPDFOCRTextEmbbedHandler() throws IOException, SAXException, TikaException {

        PDFTextParser parser = new PDFTextParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        try (InputStream stream = getStream("test-files/test_pdfProtected.pdf")) {
            parser.parse(stream, handler, metadata, context);

            String hts = handler.toString();
            assertTrue(hts.contains("RETHINKING THE FINANCIAL NETWORK"));
            assertTrue(hts.contains("This paper considers the financial system as a complex adaptive system."));
            assertTrue(hts.contains("In recent years the pace of change and innovation in financial markets and"));
            assertTrue(hts.contains("• Whose diversity was gradually eroded by institutions’"));
        }
    }

    @Test
    public void testPDFOCRTextParsingResumes() throws IOException, SAXException, TikaException {

        PDFTextParser parser = new PDFTextParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new DefaultHandler();
        ParseContext context = new ParseContext();
        try (InputStream stream = getStream("test-files/test_pdfResumes.pdf")) {
            parser.parse(stream, handler, metadata, context);
        }

    }

    @Test
    public void testPDFOCRTextResumesEmbbedHandler() throws IOException, SAXException, TikaException {

        PDFTextParser parser = new PDFTextParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        try (InputStream stream = getStream("test-files/test_pdfResumes.pdf")) {
            parser.parse(stream, handler, metadata, context);

            String hts = handler.toString();
            assertTrue(hts.contains("Freshman Resume"));
            assertTrue(hts.contains("Leadership MIT Undergraduate Giving Campaign Cambridge"));
            assertTrue(hts.contains("Manage a $1,000 budget to put on events such as “study-breaks”"));
            assertTrue(hts.contains("•   Managed 25 science journalists,"));
        }
    }

    @Test
    public void testPDFOCRTextParsingImages() throws IOException, SAXException, TikaException {

        PDFTextParser parser = new PDFTextParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        try (InputStream stream = getStream("test-files/test_pdfImages.pdf")) {
            parser.parse(stream, handler, metadata, context);
        }

    }

    @SuppressWarnings("static-access")
    @Test
    public void testPDFOCRTextImagesEmbbedMetadata() throws IOException, SAXException, TikaException {

        PDFTextParser parser = new PDFTextParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new DefaultHandler();
        ParseContext context = new ParseContext();
        try (InputStream stream = getStream("test-files/test_pdfImages.pdf")) {
            parser.parse(stream, handler, metadata, context);

            assertEquals("g2free.lo", metadata.get(TikaCoreProperties.TITLE));
            assertEquals("QuarkXPress(tm) 4.11", metadata.get(TikaCoreProperties.CREATOR_TOOL));
            assertEquals("E", metadata.get(TikaCoreProperties.CREATOR));
            assertEquals("2002-02-21", metadata.get(TikaCoreProperties.CREATED).substring(0, 10));
        }
    }

    @Test
    public void testPDFOCRTextImagesEmbbedHandler() throws IOException, SAXException, TikaException {

        System.setProperty(PDFTextParser.CREATE_THUMB, "true");
        System.setProperty(PDFTextParser.THUMB_SIZE, "600");
        PDFTextParser parser = new PDFTextParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        try (InputStream stream = getStream("test-files/test_pdfImages.pdf")) {
            parser.parse(stream, handler, metadata, context);

            String hts = handler.toString();
            assertTrue(hts.contains("Polygon-based representations of 3D"));
            assertTrue(hts.contains("finite polygon size (see Figure 1)."));
            assertTrue(hts.contains("William T. Freeman, Thouis R. Jones, and"));
            assertTrue(hts.contains("To generate our training set, we start from a collec"));
        }
    }


    @Test
    public void testPDFOCRTextThumbnail() throws IOException, SAXException, TikaException {

        System.setProperty(PDFTextParser.CREATE_THUMB, "true");
        System.setProperty(PDFTextParser.THUMB_SIZE, "600");
        PDFTextParser parser = new PDFTextParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        context.set(ComputeThumb.class, new ComputeThumb());
        try (InputStream stream = getStream("test-files/test_pdfImages.pdf")) {
            parser.parse(stream, handler, metadata, context);

            String base64Thumb = metadata.get(ExtraProperties.THUMBNAIL_BASE64);
            assertNotNull(base64Thumb);

            byte[] thumbImage = Base64.getDecoder().decode(base64Thumb);
            assertEquals(ImageIO.read(new ByteArrayInputStream(thumbImage)).getHeight(), 600);
        }
    }

}
