package iped.parsers.misc;

import iped.parsers.shareaza.ShareazaLibraryDatParser;
import iped.properties.ExtraProperties;
import junit.framework.TestCase;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;

import java.io.InputStream;

public class ThumbcacheParserTest extends TestCase {


    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    @Test
    public void testThumbcacheParserTest() throws Exception {
        ThumbcacheParser parser = new ThumbcacheParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_Thumbcache.db")) {
            parser.parse(stream, handler, metadata, context);
            String hts = handler.toString();

            assertTrue(hts.contains("Cache file format version      : 32"));
            assertTrue(hts.contains("Cache type                    : 4"));
            assertTrue(hts.contains("Offset to first cache entry    : 0"));
            assertTrue(hts.contains("Offset to first available entry: 24"));
            assertTrue(hts.contains("Number of cache entries        : 41667132"));
            assertTrue(hts.contains("Seen on Windows version        : Windows 10 / 11"));

            assertTrue(hts.contains("Entry hash                    : 0xb9dcf0e3491bfa53"));
            assertTrue(hts.contains("Entry size                    : 5936"));
            assertTrue(hts.contains("Identifier string             : b9dcf0e3491bfa53"));
            assertTrue(hts.contains("Data size                     : 5844"));
            assertTrue(hts.contains("Data checksum                 : 0x8bac679ed0019198"));
            assertTrue(hts.contains("Header checksum               : 0xc67ba0268d038d63"));

            assertTrue(hts.contains("Entry hash                    : 0x232a7b7832fef0db"));
            assertTrue(hts.contains("Entry size                    : 17250"));
            assertTrue(hts.contains("Identifier string             : 232a7b7832fef0db"));
            assertTrue(hts.contains("Data size                     : 17157"));
            assertTrue(hts.contains("Data checksum                 : 0x5f424a9955ee4e51"));
            assertTrue(hts.contains("Header checksum               : 0x3e99d8bdd31ac6b2"));

        }
    }
}
