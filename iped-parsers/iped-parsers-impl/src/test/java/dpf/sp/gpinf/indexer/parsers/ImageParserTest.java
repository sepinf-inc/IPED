package dpf.sp.gpinf.indexer.parsers;

import java.io.IOException;
import java.io.InputStream;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.image.ImageParser;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import junit.framework.TestCase;

public class ImageParserTest extends TestCase {

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    @Test
    public void testImageParsingJPEG() throws IOException, SAXException, TikaException {

        ImageParser parser = new ImageParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        metadata.set(Metadata.CONTENT_TYPE, "image/jpeg");
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_lenaJpeg.jpg")) {
            parser.parse(stream, handler, metadata, context);
            // tiff
            assertEquals("512", metadata.get(Metadata.IMAGE_LENGTH));
            assertEquals("512", metadata.get(Metadata.IMAGE_WIDTH));

        }

    }

    @Test
    public void testImageParsingPNG() throws IOException, SAXException, TikaException {

        ImageParser parser = new ImageParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new DefaultHandler();
        ParseContext context = new ParseContext();
        metadata.set(Metadata.CONTENT_TYPE, "image/png");
        // tiff
        try (InputStream stream = getStream("test-files/test_lenaPng.png")) {
            parser.parse(stream, handler, metadata, context);
            assertEquals("512", metadata.get(Metadata.IMAGE_LENGTH));
            assertEquals("512", metadata.get(Metadata.IMAGE_WIDTH));
            assertEquals("8 8 8", metadata.get(Metadata.BITS_PER_SAMPLE));

        }

    }

    @Test
    public void testImageOCRMetadataParsingTIFF() throws IOException, SAXException, TikaException {

        ImageParser parser = new ImageParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        metadata.set(Metadata.CONTENT_TYPE, "image/tiff");
        try (InputStream stream = getStream("test-files/test_lenaTiff.tiff")) {
            parser.parse(stream, handler, metadata, context);
            assertEquals("512", metadata.get(Metadata.IMAGE_LENGTH));
            assertEquals("512", metadata.get(Metadata.IMAGE_WIDTH));

        }

    }

    @Test
    public void testImageParsingBMP() throws IOException, SAXException, TikaException {

        ImageParser parser = new ImageParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        metadata.set(Metadata.CONTENT_TYPE, "image/bmp");
        try (InputStream stream = getStream("test-files/test_lenaBmp.bmp")) {
            parser.parse(stream, handler, metadata, context);
            assertEquals("512", metadata.get(Metadata.IMAGE_LENGTH));
            assertEquals("512", metadata.get(Metadata.IMAGE_WIDTH));

        }
    }

    @Test
    public void testImageParsingJP2() throws IOException, SAXException, TikaException {

        ImageParser parser = new ImageParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        metadata.set(Metadata.CONTENT_TYPE, "image/jp2");
        try (InputStream stream = getStream("test-files/test_lenaJp2.jp2")) {
            parser.parse(stream, handler, metadata, context);

        }

    }

    @Test
    public void testImageParsingGIF() throws IOException, SAXException, TikaException {

        ImageParser parser = new ImageParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        metadata.set(Metadata.CONTENT_TYPE, "image/gif");
        try (InputStream stream = getStream("test-files/test_horseGif.gif")) {
            parser.parse(stream, handler, metadata, context);
            assertEquals("342", metadata.get(Metadata.IMAGE_LENGTH));
            assertEquals("500", metadata.get(Metadata.IMAGE_WIDTH));

        }

    }
}
