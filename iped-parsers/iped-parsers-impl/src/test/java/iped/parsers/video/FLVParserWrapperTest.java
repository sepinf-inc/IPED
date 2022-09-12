package iped.parsers.video;

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

public class FLVParserWrapperTest extends TestCase {

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    @Test
    public void testFLVParserWrapperParsing() throws IOException, SAXException, TikaException {

        FLVParserWrapper parser = new FLVParserWrapper();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_videoFlv.flv")) {
            parser.parse(stream, handler, metadata, context);

        }

    }

    @Test
    public void testFLVParserWrapperEmbedded() throws IOException, SAXException, TikaException {

        FLVParserWrapper parser = new FLVParserWrapper();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_videoFlv.flv")) {
            parser.parse(stream, handler, metadata, context);

            assertEquals("2.0", metadata.get("video:videocodecid"));
            assertEquals("256.0", metadata.get("video:width"));
            assertEquals("512", metadata.get("video:minor_version"));
            assertEquals("144.0", metadata.get("video:height"));
            assertEquals("36.931", metadata.get("video:duration"));
            assertEquals("isom", metadata.get("video:major_brand"));
            assertEquals("true", metadata.get("video:stereo"));
            assertEquals("video/x-flv", metadata.get("video:Content-Type"));
            assertEquals("Lavf58.45.100", metadata.get("video:encoder"));
            assertEquals("195.3125", metadata.get("video:videodatarate"));
            assertEquals("0.0", metadata.get("video:audiodatarate"));
            assertEquals("true", metadata.get("video:hasVideo"));
            assertEquals("true", metadata.get("video:hasAudio"));
            assertEquals("29.97002997002997", metadata.get("video:framerate"));
            assertEquals("2.0", metadata.get("video:audiocodecid"));
            assertEquals("2317483.0", metadata.get("video:filesize"));
            assertEquals("44100.0", metadata.get("video:audiosamplerate"));
            assertEquals("isomiso2avc1iso6mp41", metadata.get("video:compatible_brands"));
            assertEquals("16.0", metadata.get("video:audiosamplesize"));

        }

    }

}
