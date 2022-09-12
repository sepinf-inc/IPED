package iped.parsers.browsers;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.parsers.util.ItemInfo;
import iped.properties.ExtraProperties;
import junit.framework.TestCase;

public abstract class AbstractPkgTest extends TestCase {
    public ParseContext firefoxContext;
    public ParseContext chromeContext;
    public ParseContext safariContext;
    public ParseContext safariContexthst;
    public ParseContext safariContextbkm;
    public ParseContext safariContextdwl;

    public Parser autoDetectParser;
    public EmbeddedFirefoxParser firefoxtracker;
    public EmbeddedChromeParser chrometracker;
    public EmbeddedSafariParser safaritracker;
    public EmbeddedSafariParserHst safaritrackerhst;
    public EmbeddedSafariParserBkm safaritrackerbkm;
    public EmbeddedSafariParserDwl safaritrackerdwl;
    public ItemInfo itemInfo;
    public ItemInfo itemInfohst;
    public ItemInfo itemInfobkm;
    public ItemInfo itemInfodwl;

    public void setUp() throws Exception {
        super.setUp();

        firefoxtracker = new EmbeddedFirefoxParser();
        firefoxContext = new ParseContext();
        firefoxContext.set(Parser.class, firefoxtracker);

        chrometracker = new EmbeddedChromeParser();
        chromeContext = new ParseContext();
        chromeContext.set(Parser.class, chrometracker);

        safaritracker = new EmbeddedSafariParser();
        safaritrackerhst = new EmbeddedSafariParserHst();
        safaritrackerbkm = new EmbeddedSafariParserBkm();
        safaritrackerdwl = new EmbeddedSafariParserDwl();
        safariContext = new ParseContext();
        safariContexthst = new ParseContext();
        safariContextbkm = new ParseContext();
        safariContextdwl = new ParseContext();
        ItemInfo itemInfo = new ItemInfo(0, getName(), null, null, "test-files/test_sample.plist", false);
        ItemInfo itemInfohst = new ItemInfo(0, getName(), null, null, "History", false);
        ItemInfo itemInfobkm = new ItemInfo(0, getName(), null, null, "Bookmarks", false);
        ItemInfo itemInfodwl = new ItemInfo(0, getName(), null, null, "Downloads", false);
        safariContext.set(ItemInfo.class, itemInfo);
        safariContext.set(Parser.class, safaritracker);
        safariContexthst.set(ItemInfo.class, itemInfohst);
        safariContexthst.set(Parser.class, safaritrackerhst);
        safariContextbkm.set(ItemInfo.class, itemInfobkm);
        safariContextbkm.set(Parser.class, safaritrackerbkm);
        safariContextdwl.set(ItemInfo.class, itemInfodwl);
        safariContextdwl.set(Parser.class, safaritrackerdwl);

    }

    @SuppressWarnings("serial")
    public static class EmbeddedChromeParser extends AbstractParser {

        public List<String> bookmarktitle = new ArrayList<String>();
        public List<String> bookmarkurl = new ArrayList<String>();
        public List<String> bookmarkcreated = new ArrayList<String>();
        public List<String> bookmarkmodified = new ArrayList<String>();

        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return (new AutoDetectParser()).getSupportedTypes(context);
        }

        public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
                throws IOException, SAXException, TikaException {

            if (metadata.get(TikaCoreProperties.TITLE) != null)
                bookmarktitle.add(metadata.get(TikaCoreProperties.TITLE));

            if (metadata.get(ExtraProperties.URL) != null)
                bookmarkurl.add(metadata.get(ExtraProperties.URL));

            if (metadata.get(TikaCoreProperties.CREATED) != null)
                bookmarkcreated.add(metadata.get(TikaCoreProperties.CREATED));

            if (metadata.get(TikaCoreProperties.MODIFIED) != null)
                bookmarkmodified.add(metadata.get(TikaCoreProperties.MODIFIED));

        }
    }

    @SuppressWarnings("serial")
    public static class EmbeddedSafariParserDwl extends AbstractParser {

        public List<String> downloadurl = new ArrayList<String>();
        public List<String> downloadlocalpath = new ArrayList<String>();
        public List<String> downloadtotalbytes = new ArrayList<String>();
        public List<String> downloadreceivedbytes = new ArrayList<String>();

        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return (new AutoDetectParser()).getSupportedTypes(context);
        }

        public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
                throws IOException, SAXException, TikaException {

            if (metadata.get(ExtraProperties.URL) != null)
                downloadurl.add(metadata.get(ExtraProperties.URL));

            if (metadata.get(ExtraProperties.LOCAL_PATH) != null)
                downloadlocalpath.add(metadata.get(ExtraProperties.LOCAL_PATH));

            if (metadata.get(ExtraProperties.DOWNLOAD_TOTAL_BYTES) != null)
                downloadtotalbytes.add(metadata.get(ExtraProperties.DOWNLOAD_TOTAL_BYTES));

            if (metadata.get(ExtraProperties.DOWNLOAD_RECEIVED_BYTES) != null)
                downloadreceivedbytes.add(metadata.get(ExtraProperties.DOWNLOAD_RECEIVED_BYTES));

        }
    }

    @SuppressWarnings("serial")
    public static class EmbeddedSafariParserBkm extends AbstractParser {

        public List<String> bookmarktitle = new ArrayList<String>();
        public List<String> bookmarkurl = new ArrayList<String>();

        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return (new AutoDetectParser()).getSupportedTypes(context);
        }

        public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
                throws IOException, SAXException, TikaException {

            if (metadata.get(TikaCoreProperties.TITLE) != null)
                bookmarktitle.add(metadata.get(TikaCoreProperties.TITLE));

            if (metadata.get(ExtraProperties.URL) != null)
                bookmarkurl.add(metadata.get(ExtraProperties.URL));

        }
    }

    @SuppressWarnings("serial")
    public static class EmbeddedSafariParserHst extends AbstractParser {

        public List<String> historytitle = new ArrayList<String>();
        public List<String> historyaccessed = new ArrayList<String>();
        public List<String> historyvisitdate = new ArrayList<String>();
        public List<String> historyurl = new ArrayList<String>();

        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return (new AutoDetectParser()).getSupportedTypes(context);
        }

        public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
                throws IOException, SAXException, TikaException {

            if (metadata.get(TikaCoreProperties.TITLE) != null)
                historytitle.add(metadata.get(TikaCoreProperties.TITLE));

            if (metadata.get(ExtraProperties.ACCESSED) != null)
                historyaccessed.add(metadata.get(ExtraProperties.ACCESSED));

            if (metadata.get(ExtraProperties.VISIT_DATE) != null)
                historyvisitdate.add(metadata.get(ExtraProperties.VISIT_DATE));

            if (metadata.get(ExtraProperties.URL) != null)
                historyurl.add(metadata.get(ExtraProperties.URL));

        }
    }

    @SuppressWarnings("serial")
    public static class EmbeddedSafariParser extends AbstractParser {

        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return (new AutoDetectParser()).getSupportedTypes(context);
        }

        public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
                throws IOException, SAXException, TikaException {

        }
    }

    @SuppressWarnings("serial")
    public static class EmbeddedFirefoxParser extends AbstractParser {

        public List<String> bookmarktitle = new ArrayList<String>();
        public List<String> bookmarkurl = new ArrayList<String>();
        public List<String> bookmarkcreated = new ArrayList<String>();
        public List<String> bookmarkmodified = new ArrayList<String>();

        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return (new AutoDetectParser()).getSupportedTypes(context);
        }

        public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
                throws IOException, SAXException, TikaException {

            if (metadata.get(TikaCoreProperties.TITLE) != null)
                bookmarktitle.add(metadata.get(TikaCoreProperties.TITLE));

            if (metadata.get(ExtraProperties.URL) != null)
                bookmarkurl.add(metadata.get(ExtraProperties.URL));

            if (metadata.get(TikaCoreProperties.CREATED) != null)
                bookmarkcreated.add(metadata.get(TikaCoreProperties.CREATED));

            if (metadata.get(TikaCoreProperties.MODIFIED) != null)
                bookmarkmodified.add(metadata.get(TikaCoreProperties.MODIFIED));

        }
    }
}
