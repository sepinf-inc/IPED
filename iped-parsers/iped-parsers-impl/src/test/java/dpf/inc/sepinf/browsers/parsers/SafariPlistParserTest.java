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

import dpf.sp.gpinf.indexer.parsers.util.ItemInfo;

public class SafariPlistParserTest extends AbstractPkgTest{

    private static InputStream getStream(String name) {
        return Thread.currentThread().getContextClassLoader().getResourceAsStream(name);
    } 
    
    @Test
    public void testSafariPlistParser() throws IOException, SAXException, TikaException{

        SafariPlistParser parser = new SafariPlistParser();
        Metadata metadata = new Metadata();
        metadata.add(Metadata.CONTENT_TYPE,
                MediaType.application("x-safari-plist").toString());
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_sample.plist");
        ParseContext context = new ParseContext();
        ItemInfo itemInfo = new ItemInfo(0, getName(), null, null, "test-files/test_sample.plist", false);
        context.set(ItemInfo.class, itemInfo);
        parser.getSupportedTypes(context);
        parser.setExtractEntries(true);
        try {
        parser.parse(stream, handler, metadata, safariContext);
        String hts = handler.toString();
        String mts = metadata.toString();
        System.out.println(hts + mts);
        } catch (Exception e) {
            System.out.println(e);
        }
        
    }

    
    @Test
    public void testSafariPlistParserHistory() throws IOException, SAXException, TikaException{

        SafariPlistParser parser = new SafariPlistParser();
        Metadata metadata = new Metadata();
        metadata.add(Metadata.CONTENT_TYPE,
                MediaType.application("x-safari-history").toString());
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_history.plist");
        ParseContext context = new ParseContext();
        ItemInfo itemInfo = new ItemInfo(0, getName(), null, null, "test-files/test_history.plist", false);
        context.set(ItemInfo.class, itemInfo);
        parser.getSupportedTypes(context);
        parser.setExtractEntries(true);
        try {
        parser.parse(stream, handler, metadata, safariContext);
        String hts = handler.toString();
        String mts = metadata.toString();
        System.out.println(hts + mts);
        } catch (Exception e) {
            System.out.println(e);
        }
        
    }

    @Test
    public void testSafariPlistParserBookmarks() throws IOException, SAXException, TikaException{

        SafariPlistParser parser = new SafariPlistParser();
        Metadata metadata = new Metadata();
        metadata.add(Metadata.CONTENT_TYPE,
                MediaType.application("x-safari-plist").toString());
        ContentHandler handler = new BodyContentHandler();
        InputStream stream = getStream("test-files/test_bookmarks.plist");
        ParseContext context = new ParseContext();
        ItemInfo itemInfo = new ItemInfo(0, getName(), null, null, "test-files/test_bookmarks.plist", false);
        context.set(ItemInfo.class, itemInfo);
        parser.getSupportedTypes(context);
        parser.setExtractEntries(true);
        try {
        parser.parse(stream, handler, metadata, safariContext);
        String hts = handler.toString();
        String mts = metadata.toString();
        System.out.println(hts + mts);
        } catch (Exception e) {
            System.out.println(e);
        }
        
    }
}
