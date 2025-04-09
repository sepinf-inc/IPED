package iped.parsers.misc;

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

public class ThumbcacheParserTest extends TestCase {

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    @Test
    public void testThumbcacheParser() throws IOException, SAXException, TikaException {
        ThumbcacheParser parser = new ThumbcacheParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_Thumbcache.db")) {
            parser.parse(stream, handler, metadata, context);
            String hts = handler.toString();

            final String expectedVersion = "Cache file format version      : 32";
            final String expectedType = "Cache type                    : 4";
            final String expectedOffsetFirstEntry = "Offset to first cache entry    : 0";
            final String expectedOffsetFirstAvailableEntry = "Offset to first available entry: 24";
            final String expectedNumberOfEntries = "Number of cache entries        : 41667132";
            final String expectedWindowsVersion = "Seen on Windows version        : Windows 10 / 11";

            assertTrue(hts.contains(expectedVersion));
            assertTrue(hts.contains(expectedType));
            assertTrue(hts.contains(expectedOffsetFirstEntry));
            assertTrue(hts.contains(expectedOffsetFirstAvailableEntry));
            assertTrue(hts.contains(expectedNumberOfEntries));
            assertTrue(hts.contains(expectedWindowsVersion));

            final String expectedEntryHash1 = "Entry hash                    : 0xb9dcf0e3491bfa53";
            final String expectedEntrySize1 = "Entry size                    : 5936";
            final String expectedIdentifierString1 = "Identifier string             : b9dcf0e3491bfa53";
            final String expectedDataSize1 = "Data size                     : 5844";
            final String expectedDataChecksum1 = "Data checksum                 : 0x8bac679ed0019198";
            final String expectedHeaderChecksum1 = "Header checksum               : 0xc67ba0268d038d63";

            assertTrue(hts.contains(expectedEntryHash1));
            assertTrue(hts.contains(expectedEntrySize1));
            assertTrue(hts.contains(expectedIdentifierString1));
            assertTrue(hts.contains(expectedDataSize1));
            assertTrue(hts.contains(expectedDataChecksum1));
            assertTrue(hts.contains(expectedHeaderChecksum1));

            final String expectedEntryHash2 = "Entry hash                    : 0x232a7b7832fef0db";
            final String expectedEntrySize2 = "Entry size                    : 17250";
            final String expectedIdentifierString2 = "Identifier string             : 232a7b7832fef0db";
            final String expectedDataSize2 = "Data size                     : 17157";
            final String expectedDataChecksum2 = "Data checksum                 : 0x5f424a9955ee4e51";
            final String expectedHeaderChecksum2 = "Header checksum               : 0x3e99d8bdd31ac6b2";

            assertTrue(hts.contains(expectedEntryHash2));
            assertTrue(hts.contains(expectedEntrySize2));
            assertTrue(hts.contains(expectedIdentifierString2));
            assertTrue(hts.contains(expectedDataSize2));
            assertTrue(hts.contains(expectedDataChecksum2));
            assertTrue(hts.contains(expectedHeaderChecksum2));
        }
    }
}
