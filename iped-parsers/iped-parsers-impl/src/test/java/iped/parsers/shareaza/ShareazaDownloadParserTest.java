package iped.parsers.shareaza;

import java.io.IOException;
import java.io.InputStream;

import iped.parsers.image.TiffPageParserTest;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToTextContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class ShareazaDownloadParserTest extends TiffPageParserTest {
    @Test
    public void testShareazaDownloadParser() throws IOException, SAXException, TikaException {

        String file = "test-files/test_shareazaDownload.sd";
        ParseContext context = getContext(file);
        ShareazaDownloadParser parser = new ShareazaDownloadParser();
        ContentHandler handler = new ToTextContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = getStream(file)) {
            metadata.set(Metadata.CONTENT_TYPE, "application/x-shareaza-download");
            parser.parse(stream, handler, metadata, context);

            String hts = handler.toString();

            assertTrue(hts.contains("Magic:                   SDL"));
            assertTrue(hts.contains("Version:                 42"));
            assertTrue(hts.contains("File Name:               Komodor - Electrize.mp3"));
            assertTrue(hts.contains("MD5:                     2A092D1BC6EC61272B2AF858B67FEAA0"));
            assertTrue(hts.contains("SHA1:                    6FC0E7F66C3B8059B2C3B485710FA4B09BAA4F86"));
            assertTrue(hts.contains("Serial ID:               F599F476"));
        }
    }
}
