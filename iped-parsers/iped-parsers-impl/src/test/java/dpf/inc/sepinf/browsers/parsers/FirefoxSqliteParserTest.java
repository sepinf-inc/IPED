package dpf.inc.sepinf.browsers.parsers;

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

public class FirefoxSqliteParserTest extends AbstractPkgTest{

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 
    
    @Test
    public void testFirefoxSqliteBookMarkParsing() throws IOException, SAXException, TikaException{

        FirefoxSqliteParser parser = new FirefoxSqliteParser();
        Metadata metadata = new Metadata();
        metadata.add(Metadata.CONTENT_TYPE,
                MediaType.application("x-firefox-places").toString());
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_places.sqlite");
        ParseContext context = new ParseContext();
        parser.getSupportedTypes(context);
        parser.setExtractEntries(true);
        parser.parse(stream, handler, metadata, firefoxContext);
        
        assertEquals(47, firefoxtracker.bookmarktitle.size());
        assertEquals(49, firefoxtracker.bookmarkurl.size());
        assertEquals(27, firefoxtracker.bookmarkcreated.size());
        assertEquals(27, firefoxtracker.bookmarkmodified.size());
        
        assertEquals("Help and Tutorials", firefoxtracker.bookmarktitle.get(0));
        assertEquals("Customize Firefox", firefoxtracker.bookmarktitle.get(1));
        assertEquals("Get Involved", firefoxtracker.bookmarktitle.get(2));
        assertEquals("About Us", firefoxtracker.bookmarktitle.get(3));
        assertEquals("Ubuntu", firefoxtracker.bookmarktitle.get(4));
        

        assertEquals("https://support.mozilla.org/en-US/products/firefox", firefoxtracker.bookmarkurl.get(0));
        assertEquals("https://support.mozilla.org/en-US/kb/customize-firefox-controls-buttons-and-toolbars?utm_source=firefox-browser&utm_medium="
                + "default-bookmarks&utm_campaign=customize", firefoxtracker.bookmarkurl.get(1));
        assertEquals("https://www.mozilla.org/en-US/contribute/", firefoxtracker.bookmarkurl.get(2));
        assertEquals("https://www.mozilla.org/en-US/about/", firefoxtracker.bookmarkurl.get(3));
        assertEquals("http://www.ubuntu.com/", firefoxtracker.bookmarkurl.get(4));
        

        assertEquals("2020-07-22T19:54:38Z", firefoxtracker.bookmarkcreated.get(0));
        assertEquals("2020-07-22T19:54:38Z", firefoxtracker.bookmarkcreated.get(1));
        assertEquals("2020-07-22T19:54:38Z", firefoxtracker.bookmarkcreated.get(2));
        assertEquals("2020-07-22T19:54:38Z", firefoxtracker.bookmarkcreated.get(3));
        assertEquals("2020-07-22T19:54:38Z", firefoxtracker.bookmarkcreated.get(4));
        

        assertEquals("2020-10-19T18:54:02Z", firefoxtracker.bookmarkmodified.get(0));
        assertEquals("2020-10-19T18:54:02Z", firefoxtracker.bookmarkmodified.get(1));
        assertEquals("2020-10-19T18:54:02Z", firefoxtracker.bookmarkmodified.get(2));
        assertEquals("2020-10-19T18:54:02Z", firefoxtracker.bookmarkmodified.get(3));
        assertEquals("2020-10-19T18:54:02Z", firefoxtracker.bookmarkmodified.get(4));
        
        
        
    }
}
