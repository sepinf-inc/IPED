package iped.parsers.emule;

import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.util.BaseItemSearchContext;
import iped.parsers.util.Messages;

public class PartMetParserTest extends BaseItemSearchContext {
    
    @Test
    public void testPartMetParsing() throws IOException, SAXException, TikaException {

        String file = "test-files/test_partMet.part.met";
        ParseContext context = getContext(file);
        PartMetParser parser = new PartMetParser();
        Metadata metadata = new Metadata();
        ContentHandler handler = new BodyContentHandler();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream(file)) {
            parser.parse(stream, handler, metadata, context);
            String hts = handler.toString();

            final DateFormat df = new SimpleDateFormat(Messages.getString("KnownMetParser.DataFormat"));
            df.setTimeZone(TimeZone.getTimeZone("GMT+0"));
            long lastModDateMs = 0x627a1c3f * 1000L;
            Date date = new Date(lastModDateMs);
            assertTrue(hts.contains(df.format(date)));

            String fileName = "The Strokes - Last Nite.mp3";
            String hash = "baa73b1bb93704b0225e6fc2e4b8e16d";
            String tempFileName = "001.part";
            assertTrue(hts.contains(fileName));
            assertTrue(hts.contains(hash));
            assertTrue(hts.contains(tempFileName));
        }
    }

    @Test
    public void testInvalidPartMetVersion() throws IOException {
        String file = "test-files/test_partMetInvalid.part.met";
        ParseContext context = getContext(file);
        PartMetParser parser = new PartMetParser();
        ContentHandler handler = new BodyContentHandler();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream(file)) {
            Exception exception = assertThrows(TikaException.class, () -> {
                parser.parse(stream, handler, new Metadata(), context);
            });
            String versionNotSupported = "14";
            assertTrue(exception.getMessage().contains(versionNotSupported));
        }
    }

}
