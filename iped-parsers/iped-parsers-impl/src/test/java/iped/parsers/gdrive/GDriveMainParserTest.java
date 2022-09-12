package iped.parsers.gdrive;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Database;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import junit.framework.TestCase;

public class GDriveMainParserTest extends TestCase {

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    @Test
    public void testGDriveMainGraphParsing() throws IOException, SAXException, TikaException {

        GDriveMainParser parser = new GDriveMainParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler(100000000);
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.setExtractEntries(true);
        metadata.add(Metadata.CONTENT_TYPE, GDriveMainParser.GDRIVE_CLOUD_GRAPH.toString());
        try (InputStream stream = getStream("test-files/test_cloudGraph.db")) {
            parser.parse(stream, handler, metadata, context);

            String hts = handler.toString();

            assertTrue(hts.contains("Google Drive.lnk"));
            assertTrue(hts.contains("12jDbJlY6dA7uqMuiQAEDOD43QCKz3Ird"));
            assertTrue(hts.contains("testRFC822_quoted"));
            assertTrue(hts.contains("testRFC822-multipart"));
            assertTrue(hts.contains("Md5Checker.exe"));

            assertEquals(GDriveMainParser.GDRIVE_CLOUD_GRAPH.toString(), metadata.get(Metadata.CONTENT_TYPE));

        }

    }

    @Test
    public void testGDriveMainSnapshotParsing() throws IOException, SAXException, TikaException {

        GDriveMainParser parser = new GDriveMainParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler(100000000);
        ParseContext context = new ParseContext();
        metadata.add(Metadata.CONTENT_TYPE, GDriveMainParser.GDRIVE_SNAPSHOT.toString());
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_snapshot.db")) {
            parser.parse(stream, handler, metadata, context);

            String hts = handler.toString();

            assertTrue(hts.contains("Segurança Computacional CIC"));
            assertTrue(hts.contains("Cheat Engine.lnk"));
            assertTrue(hts.contains("testRFC822_quoted"));
            assertTrue(hts.contains("mockrar5.rar"));
            assertTrue(hts.contains("a4tosticker9.png"));

            assertEquals(GDriveMainParser.GDRIVE_SNAPSHOT.toString(), metadata.get(Metadata.CONTENT_TYPE));

        }

    }

    @Test
    public void testGDriveMainAccInfoParsing() throws IOException, SAXException, TikaException {

        GDriveMainParser parser = new GDriveMainParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler(100000000);
        ParseContext context = new ParseContext();
        metadata.add(Metadata.CONTENT_TYPE, GDriveMainParser.GDRIVE_ACCOUNT_INFO.toString());
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_global.db")) {
            parser.parse(stream, handler, metadata, context);

            String hts = handler.toString();

            assertTrue(hts.contains("username_mapping"));
            assertTrue(hts.contains("2"));
            assertTrue(hts.contains("0"));
            assertTrue(hts.contains("global_preferences"));
            assertTrue(hts.contains("2"));
            assertTrue(hts.contains("3"));
            assertEquals("username_mapping", metadata.getValues(Database.TABLE_NAME)[0]);
            assertEquals("global_preferences", metadata.getValues(Database.TABLE_NAME)[1]);
            assertEquals(GDriveMainParser.GDRIVE_ACCOUNT_INFO.toString(), metadata.get(Metadata.CONTENT_TYPE));

        }
    }

    @Test
    public void testGDriveMainSyncDbParsing() throws IOException, SAXException, TikaException {

        GDriveMainParser parser = new GDriveMainParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler(100000000);
        ParseContext context = new ParseContext();
        metadata.add(Metadata.CONTENT_TYPE, GDriveMainParser.GDRIVE_ACCOUNT_INFO.toString());
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_syncConfig.db")) {
            parser.parse(stream, handler, metadata, context);

            String hts = handler.toString();

            assertTrue(hts.contains("data"));
            assertTrue(hts.contains("3"));
            assertTrue(hts.contains("41"));
            assertEquals("data", metadata.get(Database.TABLE_NAME));
            assertEquals(GDriveMainParser.GDRIVE_ACCOUNT_INFO.toString(), metadata.get(Metadata.CONTENT_TYPE));

        }
    }
}
