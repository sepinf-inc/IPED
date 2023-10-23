package iped.parsers.browsers.chrome;

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.browsers.AbstractPkgTest;
import iped.parsers.standard.StandardParser;

public class ChromeSqliteParserTest extends AbstractPkgTest {

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    }

    @Test
    public void testChromeSqliteBookmarkParser() throws IOException, SAXException, TikaException {

        ChromeSqliteParser parser = new ChromeSqliteParser();
        Metadata metadata = new Metadata();
        metadata.add(StandardParser.INDEXER_CONTENT_TYPE, MediaType.application("x-chrome-places").toString());
        ContentHandler handler = new BodyContentHandler();
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        try (InputStream stream = getStream("test-files/test_historyChrome")) {
            parser.parse(stream, handler, metadata, chromeContext);

            assertEquals(73, chrometracker.bookmarktitle.size());
            assertEquals(77, chrometracker.bookmarkurl.size());
            assertEquals(4, chrometracker.bookmarkcreated.size());
            assertEquals(0, chrometracker.bookmarkmodified.size());

            assertEquals("Entrar na conta da Microsoft", chrometracker.bookmarktitle.get(0));
            assertEquals("Entrar na conta da Microsoft", chrometracker.bookmarktitle.get(1));
            assertEquals("", chrometracker.bookmarktitle.get(2));
            assertEquals("WIP Parsers tests by streeg · Pull Request #481 · sepinf-inc/IPED",
                    chrometracker.bookmarktitle.get(3));
            assertEquals("Sign in to GitHub · GitHub", chrometracker.bookmarktitle.get(4));

            assertEquals("https://github.com/streeg/processo-pf/archive/refs/heads/master.zip",
                    chrometracker.bookmarkurl.get(0));
            assertEquals("https://codeload.github.com/streeg/processo-pf/zip/refs/heads/master",
                    chrometracker.bookmarkurl.get(1));
            assertEquals("https://github.com/streeg/streeg/archive/refs/heads/master.zip",
                    chrometracker.bookmarkurl.get(2));
            assertEquals("https://codeload.github.com/streeg/streeg/zip/refs/heads/master",
                    chrometracker.bookmarkurl.get(3));

            assertEquals("2021-05-12T17:41:52Z", chrometracker.bookmarkcreated.get(0));
            assertEquals("2021-05-12T17:41:52Z", chrometracker.bookmarkcreated.get(1));
            assertEquals("2021-05-12T17:42:03Z", chrometracker.bookmarkcreated.get(2));
            assertEquals("2021-05-12T17:42:03Z", chrometracker.bookmarkcreated.get(3));

        }

    }
}
