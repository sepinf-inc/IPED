package iped.parsers.bittorrent;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class TorrentFileParserTest extends TorrentTestCase {

    @Test
    public void testBitTorrentFileSimpleParsing() throws IOException, SAXException, TikaException {

        TorrentFileParser parser = new TorrentFileParser();
        Metadata metadata = new Metadata();
        metadata.add(Metadata.CONTENT_TYPE, TorrentFileParser.TORRENT_FILE_MIME_TYPE.toString());
        ContentHandler handler = new BodyContentHandler(-1);
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_torrentSimple.torrent")) {
            parser.parse(stream, handler, metadata, context);

            String res = clean(handler.toString());

            assertTrue(res.contains("NovaROFull_19112020.exe"));
            assertTrue(res.contains("3259111196"));
        }
    }

    @Test
    public void testBitTorrentFileMultipleParsing() throws IOException, SAXException, TikaException {

        TorrentFileParser parser = new TorrentFileParser();
        Metadata metadata = new Metadata();
        metadata.add(Metadata.CONTENT_TYPE, TorrentFileParser.TORRENT_FILE_MIME_TYPE.toString());
        ContentHandler handler = new BodyContentHandler(-1);
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_torrentMultiple.torrent")) {
            parser.parse(stream, handler, metadata, context);

            String res = clean(handler.toString());

            assertTrue(res.contains("Big Buck Bunny/Big Buck Bunny.mp4"));
            assertTrue(res.contains("Big Buck Bunny/Big Buck Bunny.en.srt"));
            assertTrue(res.contains("140"));
            assertTrue(res.contains("276134947"));
            assertTrue(res.contains("310380"));
        }
    }
}
