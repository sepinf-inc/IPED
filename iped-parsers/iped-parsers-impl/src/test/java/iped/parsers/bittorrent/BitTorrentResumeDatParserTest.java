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

public class BitTorrentResumeDatParserTest extends TorrentTestCase {

    @Test
    public void testBitTorrentResumeParsing() throws IOException, SAXException, TikaException {

        BitTorrentResumeDatParser parser = new BitTorrentResumeDatParser();
        Metadata metadata = new Metadata();
        metadata.add(Metadata.CONTENT_TYPE, BitTorrentResumeDatParser.RESUME_DAT_MIME_TYPE.toString());
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_resume.dat")) {
            parser.parse(stream, handler, metadata, context);

            String res = clean(handler.toString());

            assertTrue(res.contains("Sintel.torrent"));
            assertTrue(res.contains("63831927"));
            assertTrue(res.contains("Tears of Steel.torrent"));
            assertTrue(res.contains("29736960"));
            assertTrue(res.contains("NovaROFull_19112020.exe.torrent"));
            assertTrue(res.contains("3259111196"));
            assertTrue(res.contains("Cosmos Laundromat.torrent"));
            assertTrue(res.contains("23551574"));
            assertTrue(res.contains("Big Buck Bunny.torrent"));
            assertTrue(res.contains("56228123"));

        }

    }
}
