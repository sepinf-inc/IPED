package iped.parsers.shareaza;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToTextContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import junit.framework.TestCase;

public class ShareazaDownloadParserTest extends TestCase {

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }


    @Test
    public void testShareazaDownloadParser() throws IOException, SAXException, TikaException {
        ShareazaDownloadParser parser = new ShareazaDownloadParser();
        ToTextContentHandler handler = new ToTextContentHandler();
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);

        try (InputStream stream = getStream("test-files/test_shareazaDownload.sd")) {
            parser.parse(stream, handler, metadata, context);

            String parsedText = handler.toString();

            assertTrue(parsedText.contains("Komodor - Electrize.mp3"));
            assertTrue(parsedText.contains("File Length:             5,613,696 Bytes (5.4 MB)"));
            assertTrue(parsedText.contains("Number of Fragments: 1"));
            assertTrue(parsedText.contains("SHA1:                    6FC0E7F66C3B8059B2C3B485710FA4B09BAA4F86"));
            assertTrue(parsedText.contains("Source Address:                 http://189.60.225.131:6346/uri-res/N2R?urn:sha1:N7AOP5TMHOAFTMWDWSCXCD5EWCN2UT4G"));
            assertTrue(parsedText.contains("Server Name:                    04 - Artist - Komodor - electrize.mp3"));
        }
    }
}
