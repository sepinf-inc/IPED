package iped.parsers.tor;

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

public class TorParserTest extends TestCase {

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    @Test
    public void testTorParser1() throws IOException, SAXException, TikaException {
        TorTcParser parser = new TorTcParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/testTor1")) {
            parser.parse(stream, handler, metadata, context);
            assertEquals("IS_INTERNAL,NEED_CAPACITY", metadata.get(TorTcParser.TORTC_BUILD_FLAGS));
            assertEquals("HS_CLIENT_REND", metadata.get(TorTcParser.TORTC_PURPOSE));
            assertEquals("facebookwkhpilnemxj7asaniu7vnjjbiltxjqhye3mhbshg7kx5tfyd", metadata.get(TorTcParser.TORTC_REND_QUERY));
            assertEquals("2023-07-03T17:05:24.214215Z", metadata.get(TorTcParser.TORTC_TIME_CREATED));
            assertEquals("\"facebookwkhpilnemxj7asaniu7vnjjbiltxjqhye3mhbshg7kx5tfyd.onion\"", metadata.get(TorTcParser.TORTC_SOCKS_USERNAME));
            assertEquals("\"8b855a9b98c96ce9b877a17397d59945\"", metadata.get(TorTcParser.TORTC_SOCKS_PASSWORD));
        }
    }

    @Test
    public void testTorParser2() throws IOException, SAXException, TikaException {
        TorTcParser parser = new TorTcParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/testTor2")) {
            parser.parse(stream, handler, metadata, context);
            assertEquals("IS_INTERNAL,NEED_CAPACITY", metadata.get(TorTcParser.TORTC_BUILD_FLAGS));
            assertEquals("HS_CLIENT_REND", metadata.get(TorTcParser.TORTC_PURPOSE));
            assertEquals("facebook26qderizo52pigg5y4a2jsdhqz4odvvusaij4yhxehqngqad", metadata.get(TorTcParser.TORTC_REND_QUERY));
            assertEquals("2023-07-03T17:04:59.965391Z", metadata.get(TorTcParser.TORTC_TIME_CREATED));
            assertEquals("\"facebookwkhpilnemxj7asaniu7vnjjbiltxjqhye3mhbshg7kx5tfyd.onion\"", metadata.get(TorTcParser.TORTC_SOCKS_USERNAME));
            assertEquals("\"8b855a9b98c96ce9b877a17397d59945\"", metadata.get(TorTcParser.TORTC_SOCKS_PASSWORD));
        }
    }
}