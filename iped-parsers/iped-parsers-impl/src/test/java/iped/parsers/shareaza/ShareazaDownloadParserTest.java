package iped.parsers.shareaza;

import java.io.IOException;
import java.io.InputStream;

import iped.parsers.util.BaseItemSearchContext;
import iped.properties.ExtraProperties;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.sax.ToTextContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class ShareazaDownloadParserTest extends BaseItemSearchContext {
    @Test
    public void testShareazaDownloadParser() throws IOException, SAXException, TikaException {
        String file = "test-files/test_shareazaDownload.sd";
        ParseContext context = getContext(file);
        ShareazaDownloadParser parser = new ShareazaDownloadParser();
        ContentHandler handler = new ToTextContentHandler();
        Metadata metadata = new Metadata();

        try (InputStream stream = getStream(file)) {
            parser.parse(stream, handler, metadata, context);

            String hts = handler.toString();

            assertTrue(hts.contains("Magic:                   SDL"));
            assertTrue(hts.contains("Version:                 42"));
            assertTrue(hts.contains("File Name:               Komodor - Electrize.mp3"));
            assertTrue(hts.contains("Search Terms:            <none present>"));
            assertTrue(hts.contains("SHA1:                    6FC0E7F66C3B8059B2C3B485710FA4B09BAA4F86"));
            assertTrue(hts.contains("TIGER:                   CF9F77A8B2DB58844EE3CB6E3C68D832A333E22980D11EAC"));
            assertTrue(hts.contains("MD5:                     2A092D1BC6EC61272B2AF858B67FEAA0"));
            assertTrue(hts.contains("EDONKEY:                 46A856AC472CC0B238258AFCB9612A83"));
            assertTrue(hts.contains("Expanded:                false"));
            assertTrue(hts.contains("Paused:                  false"));
            assertTrue(hts.contains("Boosted:                 false"));
            assertTrue(hts.contains("Shared:                  true"));
            assertTrue(hts.contains("Serial ID:               F599F476"));

            assertEquals(false, Boolean.parseBoolean(metadata.get("p2p:shared"))); // false because p2p:totalDownloaded == 0
            assertEquals("5613696", metadata.get("p2p:fileSize"));
            assertEquals("0", metadata.get("p2p:totalDownloaded"));
            assertEquals("1", metadata.get("p2pHistoryEntries"));
            assertEquals(ShareazaDownloadParser.SHAREAZA_DOWNLOAD_META, metadata.get(Metadata.CONTENT_TYPE));

            assertEquals(5, metadata.size());
        }
    }
}
