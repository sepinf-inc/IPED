package iped.parsers.mail;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.util.AbstractPkgTest;

public class OutlookDBXParserTest extends AbstractPkgTest {

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    @Test
    public void testOutlookDBXParser() throws IOException, SAXException, TikaException {

        OutlookDBXParser parser = new OutlookDBXParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        parser.getSupportedTypes(trackingContext);

        try (InputStream stream = getStream("test-files/test_entryBox.dbx")) {
            parser.parse(stream, handler, metadata, trackingContext);
            assertEquals(368, tracker.subitemCount);
            assertEquals(368, tracker.itensmd5.size());
            assertEquals(0, tracker.modifieddate.size());
            assertEquals(0, tracker.folderCount);
            assertEquals(0, tracker.filenames.size());

            assertEquals("F9E402BF2E5F867EE3102F2E70AECA33", tracker.itensmd5.get(0));
            assertEquals("01150CB085A0CFA5127B2B2F05A01E43", tracker.itensmd5.get(1));
            assertEquals("804C85BC06030156C96C9981458D5793", tracker.itensmd5.get(2));
            assertEquals("9381A14DBE23EC56F857C4A7D97840FF", tracker.itensmd5.get(3));
            assertEquals("F467400CD26CDCEE4E03F97C391B4A96", tracker.itensmd5.get(4));

            assertEquals("F1E04F63DF94809FD24F175F8EC4715F", tracker.itensmd5.get(363));
            assertEquals("422DC298BBB82BF806472F5E15FF19C2", tracker.itensmd5.get(364));
            assertEquals("FDA22E2B50D1EACD953EEEF84AB46137", tracker.itensmd5.get(365));
            assertEquals("374D80B040F4BC91C18C3E93BDBB75B3", tracker.itensmd5.get(366));
            assertEquals("EEB1A3B43E62771DA5B6141BC9633497", tracker.itensmd5.get(367));

        }
    }

}