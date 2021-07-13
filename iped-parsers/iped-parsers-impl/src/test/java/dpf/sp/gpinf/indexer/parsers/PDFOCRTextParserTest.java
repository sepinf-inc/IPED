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
import junit.framework.TestCase;

public class PDFOCRTextParserTest extends TestCase{

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }
    
    @Test
    public void testPDFOCRTextParsing() throws IOException, SAXException, TikaException{

        PDFOCRTextParser parser = new PDFOCRTextParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new DefaultHandler();
        ParseContext context = new ParseContext();
        InputStream stream = getStream("test-files/test_pdfProtected.pdf");
        parser.getSupportedTypes(context);
        parser.parse(stream, handler, metadata, context);
        
    }
    
    @SuppressWarnings("deprecation")
    @Test
    public void testPDFOCRTextParsingICE() throws IOException, SAXException, TikaException{

        PDFOCRTextParser parser = new PDFOCRTextParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new DefaultHandler();
        ParseContext context = new ParseContext();
        InputStream stream = getStream("test-files/test_pdfProtected.pdf");
        parser.getSupportedTypes(context);
        parser.setUseIcePDF(true);
        parser.parse(stream, handler, metadata, context);
        
        String mts = metadata.toString();
        
        assertEquals("PScript5.dll Version 5.2", metadata.get(Metadata.CREATOR));
        assertEquals("Speeches by Andrew G Haldane", metadata.get(Metadata.SUBJECT));
        assertEquals("The Bank of England", metadata.get(Metadata.AUTHOR));
        assertEquals("April 28, 2009 10:06:56 (UTC +01:00)", metadata.get(Metadata.CREATION_DATE));
        assertEquals("Rethinking the Financial Network, Speech by Andrew G Haldane, Executive Director, Financial Stability delivered at the Financial Student Association, Amsterdam on 28 April 2009", metadata.get(Metadata.TITLE));
        assertEquals("application/pdf", metadata.get(Metadata.CONTENT_TYPE));
        assertTrue(mts.contains("Content-Type=application/pdf"));
        
    }

    @SuppressWarnings({ "deprecation", "static-access" })
    @Test
    public void testPDFOCRTextEmbbedMetadata() throws IOException, SAXException, TikaException{

        PDFOCRTextParser parser = new PDFOCRTextParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new DefaultHandler();
        ParseContext context = new ParseContext();
        InputStream stream = getStream("test-files/test_pdfProtected.pdf");
        parser.parse(stream, handler, metadata, context);
        assertEquals("Rethinking the Financial Network, Speech by Andrew G Haldane,"
                + " Executive Director, Financial Stability delivered at the Financial"
                + " Student Association, Amsterdam on 28 April 2009",
                metadata.get(TikaCoreProperties.TITLE));
        assertEquals("Speeches by Andrew G Haldane", metadata.get(Metadata.SUBJECT));
        assertEquals("The Bank of England", metadata.get(TikaCoreProperties.CREATOR));
        assertEquals(metadata.get(TikaCoreProperties.CREATOR), metadata.get(metadata.AUTHOR));
        assertEquals("Speeches by Andrew G Haldane", metadata.get(metadata.DESCRIPTION));

        
    }
    
    @Test
    public void testPDFOCRTextEmbbedHandler() throws IOException, SAXException, TikaException{

        PDFOCRTextParser parser = new PDFOCRTextParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        InputStream stream = getStream("test-files/test_pdfProtected.pdf");
        parser.parse(stream, handler, metadata, context);
        String bodyText = handler.toString();
        assertTrue(bodyText.contains("RETHINKING THE FINANCIAL NETWORK"));
        assertTrue(bodyText.contains("This paper considers the financial system as a complex adaptive system."));
        assertTrue(bodyText.contains("In recent years the pace of change and innovation in financial markets and"));
        assertTrue(bodyText.contains("• Whose diversity was gradually eroded by institutions’"));
        
    }
    @Test
    public void testPDFOCRTextParsingResumes() throws IOException, SAXException, TikaException{

        PDFOCRTextParser parser = new PDFOCRTextParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new DefaultHandler();
        ParseContext context = new ParseContext();
        InputStream stream = getStream("test-files/test_pdfResumes.pdf");
        parser.parse(stream, handler, metadata, context);
        
        
    }


    @Test
    public void testPDFOCRTextResumesEmbbedHandler() throws IOException, SAXException, TikaException{

        PDFOCRTextParser parser = new PDFOCRTextParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        InputStream stream = getStream("test-files/test_pdfResumes.pdf");
        parser.parse(stream, handler, metadata, context);
        String bodyText = handler.toString();
        assertTrue(bodyText.contains("Freshman Resume"));
        assertTrue(bodyText.contains("Leadership MIT Undergraduate Giving Campaign Cambridge"));
        assertTrue(bodyText.contains("Manage a $1,000 budget to put on events such as “study-breaks”"));
        assertTrue(bodyText.contains("•   Managed 25 science journalists,"));
      
    }     
    
    
    @Test
    public void testPDFOCRTextParsingImages() throws IOException, SAXException, TikaException{

        PDFOCRTextParser parser = new PDFOCRTextParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        InputStream stream = getStream("test-files/test_pdfImages.pdf");
        parser.parse(stream, handler, metadata, context);

        
        
    }
    @SuppressWarnings({ "deprecation", "static-access" })
    @Test
    public void testPDFOCRTextImagesEmbbedMetadata() throws IOException, SAXException, TikaException{

        PDFOCRTextParser parser = new PDFOCRTextParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new DefaultHandler();
        ParseContext context = new ParseContext();
        InputStream stream = getStream("test-files/test_pdfImages.pdf");
        parser.parse(stream, handler, metadata, context);
        assertEquals("g2free.lo", metadata.get(TikaCoreProperties.TITLE));
        assertEquals("QuarkXPress(tm) 4.11", metadata.get(TikaCoreProperties.CREATOR_TOOL));
        assertEquals("E", metadata.get(TikaCoreProperties.CREATOR));
        assertEquals(metadata.get(TikaCoreProperties.CREATOR), metadata.get(metadata.AUTHOR));
        assertEquals("2002-02-21", metadata.get(metadata.CREATION_DATE).substring(0,10));

    }
    @Test
    public void testPDFOCRTextImagesEmbbedHandler() throws IOException, SAXException, TikaException{

        PDFOCRTextParser parser = new PDFOCRTextParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        InputStream stream = getStream("test-files/test_pdfImages.pdf");
        parser.parse(stream, handler, metadata, context);
        String bodyText = handler.toString();
        assertTrue(bodyText.contains("Polygon-based representations of 3D"));
        assertTrue(bodyText.contains("finite polygon size (see Figure 1)."));
        assertTrue(bodyText.contains("William T. Freeman, Thouis R. Jones, and"));
        assertTrue(bodyText.contains("To generate our training set, we start from a collec"));
        
    }
    
}
