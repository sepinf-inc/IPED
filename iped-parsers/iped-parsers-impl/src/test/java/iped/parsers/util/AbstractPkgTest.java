package iped.parsers.util;

import static org.apache.commons.codec.digest.MessageDigestAlgorithms.MD5;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import org.apache.commons.codec.digest.DigestUtils;
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

import iped.properties.ExtraProperties;
import junit.framework.TestCase;

public abstract class AbstractPkgTest extends TestCase {
    protected ParseContext trackingContext;
    protected ParseContext recursingContext;

    protected Parser autoDetectParser;
    protected EmbeddedTrackingParser tracker;

    protected Date parseFromDefaultDateFormat(String value) throws ParseException {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        df.setTimeZone(TimeZone.getTimeZone("GMT"));
        return df.parse(value);
    }

    protected void setUp() throws Exception {
        super.setUp();

        tracker = new EmbeddedTrackingParser();
        trackingContext = new ParseContext();
        trackingContext.set(Parser.class, tracker);

        ItemInfo itemInfo = new ItemInfo(0, MD5, null, null, MD5, false);
        autoDetectParser = new AutoDetectParser();
        recursingContext = new ParseContext();
        recursingContext.set(Parser.class, autoDetectParser);
        recursingContext.set(ItemInfo.class, itemInfo);
    }

    @SuppressWarnings("serial")
    protected static class EmbeddedTrackingParser extends AbstractParser {
        public List<String> filenames = new ArrayList<String>();
        public List<String> modifieddate = new ArrayList<String>();
        public List<String> itensmd5 = new ArrayList<String>();
        public List<String> isfolder = new ArrayList<String>();
        public int subitemCount = 0;
        public int folderCount = 0;

        public Set<MediaType> getSupportedTypes(ParseContext context) {
            return (new AutoDetectParser()).getSupportedTypes(context);
        }

        public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
                throws IOException, SAXException, TikaException {

            subitemCount++;
            String hdigest = new DigestUtils(MD5).digestAsHex(stream);
            if (metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY) != null)
                filenames.add(metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY));
            if (metadata.get(TikaCoreProperties.MODIFIED) != null)
                modifieddate.add(metadata.get(TikaCoreProperties.MODIFIED));
            itensmd5.add(hdigest.toUpperCase());
            if (Boolean.valueOf(metadata.get(ExtraProperties.EMBEDDED_FOLDER))) {
                isfolder.add("true");
                folderCount++;
            } else {
                isfolder.add("false");
            }

        }

    }

}
