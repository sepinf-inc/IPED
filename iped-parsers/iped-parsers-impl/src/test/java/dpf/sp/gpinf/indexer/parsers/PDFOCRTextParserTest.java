package dpf.sp.gpinf.indexer.parsers;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import dpf.sp.gpinf.indexer.parsers.util.ComputeThumb;
import iped3.util.ExtraProperties;
import junit.framework.TestCase;

public class PDFOCRTextParserTest extends TestCase {

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    @Test
    public void testPDFOCRTextParsing() throws IOException, SAXException, TikaException {

        PDFOCRTextParser parser = new PDFOCRTextParser();
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

        PDFOCRTextParser parser = new PDFOCRTextParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new DefaultHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.setUseIcePDF(true);
        try (InputStream stream = getStream("test-files/test_pdfProtected.pdf")) {
            parser.parse(stream, handler, metadata, context);

            String mts = metadata.toString();

            assertEquals("PScript5.dll Version 5.2", metadata.getValues(TikaCoreProperties.CREATOR)[1]);
            assertEquals("Acrobat Distiller 7.0.5 (Windows)", metadata.get(TikaCoreProperties.CREATOR_TOOL));
            assertEquals("Speeches by Andrew G Haldane", metadata.get(TikaCoreProperties.SUBJECT));
            assertEquals("The Bank of England", metadata.get(TikaCoreProperties.CREATOR));
            assertEquals("April 28, 2009 10:06:56 (UTC +01:00)", metadata.get(TikaCoreProperties.CREATED));
            assertEquals(
                    "Rethinking the Financial Network, Speech by Andrew G Haldane, Executive Director, Financial Stability delivered at the Financial Student Association, Amsterdam on 28 April 2009",
                    metadata.get(TikaCoreProperties.TITLE));
            assertEquals("application/pdf", metadata.get(Metadata.CONTENT_TYPE));
            assertTrue(mts.contains("Content-Type=application/pdf"));
        }

    }

    @SuppressWarnings("static-access")
    @Test
    public void testPDFOCRTextEmbbedMetadata() throws IOException, SAXException, TikaException {

        PDFOCRTextParser parser = new PDFOCRTextParser();
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

        PDFOCRTextParser parser = new PDFOCRTextParser();
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

        PDFOCRTextParser parser = new PDFOCRTextParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new DefaultHandler();
        ParseContext context = new ParseContext();
        try (InputStream stream = getStream("test-files/test_pdfResumes.pdf")) {
            parser.parse(stream, handler, metadata, context);
        }

    }

    @Test
    public void testPDFOCRTextResumesEmbbedHandler() throws IOException, SAXException, TikaException {

        PDFOCRTextParser parser = new PDFOCRTextParser();
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

        PDFOCRTextParser parser = new PDFOCRTextParser();
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

        PDFOCRTextParser parser = new PDFOCRTextParser();
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

        System.setProperty(PDFOCRTextParser.CREATE_THUMB, "true");
        System.setProperty(PDFOCRTextParser.THUMB_SIZE,"600");
        PDFOCRTextParser parser = new PDFOCRTextParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        context.set(ComputeThumb.class, new ComputeThumb());
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

        System.setProperty(PDFOCRTextParser.CREATE_THUMB, "true");
        System.setProperty(PDFOCRTextParser.THUMB_SIZE,"600");
        PDFOCRTextParser parser = new PDFOCRTextParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        context.set(ComputeThumb.class, new ComputeThumb());
        try (InputStream stream = getStream("test-files/test_pdfImages.pdf")) {
            parser.parse(stream, handler, metadata, context);

            String base64Thumb = metadata.get(ExtraProperties.THUMBNAIL_BASE64);

            assertTrue(base64Thumb != null);
            // assertTrue(base64Thumb.contains("/9j/4AAQSkZJRgABAgAAAQABAAD/2wBDAAgGBgcGBQgHB"));
            // assertTrue(base64Thumb.contains("+Ff/JL/AA9/16D+ZrsK4/4V/wDJL/D3/XoP5muwoAKKK"));
            // assertTrue(base64Thumb.contains("eU2D39qT/AIS+NC5ktwcRh9kb7iOZcjPGWPlYC46nr6A"));
            // assertTrue(base64Thumb.contains("BUhzIAMryuXJwc4zxigCsulaFeC3ujebjKRLF5jJ829W"));
            // assertTrue(base64Thumb.contains("PC/jx4l0bxLrekTaNqMN7HDbOsjREkKS2cUUUUAf/9k="));

        }
    }

}
