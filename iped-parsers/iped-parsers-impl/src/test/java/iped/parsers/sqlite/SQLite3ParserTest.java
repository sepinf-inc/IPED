package iped.parsers.sqlite;

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

public class SQLite3ParserTest extends TestCase {

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    @Test
    public void testSqlite3Parser() throws IOException, SAXException, TikaException {

        SQLite3Parser parser = new SQLite3Parser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        try (InputStream stream = getStream("test-files/test_global.db")) {
            parser.parse(stream, handler, metadata, context);

            String hts = handler.toString();

            assertTrue(hts.contains("Name"));
            assertTrue(hts.contains("Cols"));
            assertTrue(hts.contains("Rows"));
            assertTrue(hts.contains("username_mapping"));
            assertTrue(hts.contains("2"));
            assertTrue(hts.contains("0"));
            assertTrue(hts.contains("global_preferences"));
            assertTrue(hts.contains("2"));
            assertTrue(hts.contains("3"));

            assertEquals(metadata.getValues(Database.TABLE_NAME)[0], "username_mapping");
            assertEquals(metadata.getValues(Database.TABLE_NAME)[1], "global_preferences");
        }
    }

    @Test
    public void testSqlite3Parser2() throws IOException, SAXException, TikaException {

        SQLite3Parser parser = new SQLite3Parser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        try (InputStream stream = getStream("test-files/test_activitiesCache.db")) {
            parser.parse(stream, handler, metadata, context);
            SQLite3Parser.getConnectionProperties();
            String hts = handler.toString();

            assertTrue(hts.contains("Name"));
            assertTrue(hts.contains("Cols"));
            assertTrue(hts.contains("Rows"));
            assertTrue(hts.contains("Activity"));
            assertTrue(hts.contains("31"));
            assertTrue(hts.contains("Metadata"));
            assertTrue(hts.contains("2"));
            assertTrue(hts.contains("6699"));

            assertEquals(metadata.getValues(Database.TABLE_NAME)[0], "Activity");
            assertEquals(metadata.getValues(Database.TABLE_NAME)[1], "ActivityOperation");

        }
    }

}
