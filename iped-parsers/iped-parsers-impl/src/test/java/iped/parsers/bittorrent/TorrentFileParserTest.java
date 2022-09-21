package iped.parsers.bittorrent;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import junit.framework.TestCase;

public class TorrentFileParserTest extends TestCase {

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    @Test
    public void testBitTorrentFileSimpleParsing() throws IOException, SAXException, TikaException {

        TorrentFileParser parser = new TorrentFileParser();
        Metadata metadata = new Metadata();
        metadata.add(Metadata.CONTENT_TYPE, TorrentFileParser.TORRENT_FILE_MIME_TYPE.toString());
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_torrentSimple.torrent")) {
            parser.parse(stream, handler, metadata, context);

            String hts = handler.toString();

            assertTrue(hts.contains("NovaROFull_19112020.exe"));
            assertTrue(hts.contains("3259111196"));

        }

    }

    @Test
    public void testBitTorrentFileMultipleParsing() throws IOException, SAXException, TikaException {

        TorrentFileParser parser = new TorrentFileParser();
        Metadata metadata = new Metadata();
        metadata.add(Metadata.CONTENT_TYPE, TorrentFileParser.TORRENT_FILE_MIME_TYPE.toString());
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_torrentMultiple.torrent")) {
            parser.parse(stream, handler, metadata, context);

            String hts = handler.toString();

            assertTrue(hts.contains("Big Buck Bunny"));
            assertTrue(hts.contains("Big Buck Bunny.mp4"));
            assertTrue(hts.contains("Big Buck Bunny.en.srt"));
            assertTrue(hts.contains("140"));
            assertTrue(hts.contains("276134947"));
            assertTrue(hts.contains("310380"));

        }

    }
}
