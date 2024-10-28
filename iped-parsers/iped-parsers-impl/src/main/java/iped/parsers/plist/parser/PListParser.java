package iped.parsers.plist.parser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.util.Collections;
import java.util.Date;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.tika.detect.apple.BPListDetector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.dd.plist.NSArray;
import com.dd.plist.NSData;
import com.dd.plist.NSDate;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSNumber;
import com.dd.plist.NSObject;
import com.dd.plist.NSSet;
import com.dd.plist.NSString;
import com.dd.plist.PropertyListFormatException;
import com.dd.plist.PropertyListParser;
import com.dd.plist.UID;

import iped.parsers.util.IgnoreContentHandler;
import iped.parsers.util.MetadataUtil;
import iped.properties.BasicProps;
import iped.utils.DateUtil;
import iped.utils.IOUtil;

/***
 * 
 * @author PCF Patrick Dalla Bernardina
 *
 *         This parser formats the bplist data in a expandable html tree,
 *         extract binary data as subitens and NSDate objects as metadata.
 */
public class PListParser extends AbstractParser {

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(BPListDetector.BPLIST);
    private static final String BPLIST_METADATA_SUFFIX = "bplist";

    private static final String CSS = new String(readResourceAsBytes("/iped/parsers/css/treeview.css"), Charset.forName("UTF-8"));
    private static final String UUIDJS = new String(readResourceAsBytes("/iped/parsers/css/uuidlink.js"), Charset.forName("UTF-8"));

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext arg0) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream is, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {
        if (is instanceof TikaInputStream) {
            NSObject oc = null;
            try {
                if (is instanceof TikaInputStream && ((TikaInputStream) is).hasFile()) {
                    oc = PropertyListParser.parse(((TikaInputStream) is).getFile());

                } else {
                    oc = PropertyListParser.parse(is);
                }


                XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
                xhtml.startDocument();

                xhtml.startElement("style");
                xhtml.characters(PListParser.CSS);
                xhtml.endElement("style");

                try {
                    EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class, new ParsingEmbeddedDocumentExtractor(context));

                    xhtml.startElement("nav");
                    parseNSObject(oc, xhtml, metadata, getBasePath(), extractor, context);
                    xhtml.endElement("nav");
                } catch (Exception e) {
                    e.printStackTrace();
                }

                xhtml.startElement("script");
                xhtml.characters(PListParser.UUIDJS);
                xhtml.endElement("script");
                xhtml.endDocument();
            } catch (IOException | PropertyListFormatException | ParseException | ParserConfigurationException | SAXException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    public String getBasePath() {
        return BPLIST_METADATA_SUFFIX;
    }

    public boolean parseNSPrimitiveObject(NSObject nso, XHTMLContentHandler xhtml, Metadata metadata, String path, EmbeddedDocumentExtractor extractor, ParseContext context) throws SAXException {
        if (nso instanceof UID) {
            parseUID((UID) nso, xhtml, metadata, path, extractor, context);
            return true;
        }
        if (nso instanceof NSNumber) {
            NSNumber n = (NSNumber) nso;
            Date now = new Date();
            // converts 30 years to now and 2 years from now timestamps
            if (n.longValue() > (now.getTime() / 1000) - 3600 * 24 * 365 * 30 && n.longValue() < (now.getTime() / 1000) + 3600 * 24 * 365 * 2) {
                try {
                    Date date = new Date(n.longValue() * 1000);
                    // check to see if it is a date information
                    xhtml.startElement("li", "class", "nochild");
                    xhtml.characters(nso.toString() + "(" + DateUtil.dateToString(date) + ")");
                    xhtml.endElement("li");
                    MetadataUtil.setMetadataType(path, Date.class);
                    String dateStr = DateUtil.dateToString(date);
                    metadata.add(path, dateStr);
                } catch (Exception e) {
                    xhtml.startElement("li", "class", "nochild");
                    xhtml.characters(escapeEmpty(nso.toString()));
                    xhtml.endElement("li");
                }
            } else {
                xhtml.startElement("li", "class", "nochild");
                xhtml.characters(escapeEmpty(nso.toString()));
                xhtml.endElement("li");
            }
            return true;
        }
        if (nso instanceof NSString) {
            xhtml.startElement("li", "class", "nochild");
            xhtml.characters(escapeEmpty(nso.toString()));
            xhtml.endElement("li");
            return true;
        }
        if (nso instanceof NSDate) {
            String dateStr = DateUtil.dateToString(((NSDate) nso).getDate());
            xhtml.startElement("li", "class", "nochild");
            xhtml.characters(dateStr);
            xhtml.endElement("li");
            MetadataUtil.setMetadataType(path, Date.class);
            metadata.add(path, dateStr);
            return true;
        }
        return false;
    }

    public String escapeEmpty(String str) {
        // this method was implemented to avoid empty content inside tag was causing CSS
        // not to correctly apply
        if (str.equals("")) {
            return " ";
        }
        return str;
    }

    public void parseUID(UID nso, XHTMLContentHandler xhtml, Metadata metadata, String path, EmbeddedDocumentExtractor extractor, ParseContext context) throws SAXException {
        xhtml.startElement("li", "class", "nochild");
        xhtml.characters(((UID) nso).getName());
        xhtml.endElement("li");
    }

    public void parseNSObject(NSObject nso, XHTMLContentHandler xhtml, Metadata metadata, String path, EmbeddedDocumentExtractor extractor, ParseContext context) throws SAXException {
        if (nso instanceof NSDictionary) {
            if (((NSDictionary) nso).size() <= 0) {
                return;
            }
        }
        if (nso instanceof NSArray) {
            if (((NSArray) nso).getArray().length <= 0) {
                return;
            }
        }
        if (nso instanceof NSSet) {
            if (((NSSet) nso).allObjects().length <= 0) {
                return;
            }
        }
        if (parseNSPrimitiveObject(nso, xhtml, metadata, path, extractor, context)) {
            return;
        }

        if ((nso instanceof NSData)) {
            try {
                if (path.contains("MSAppCenterPastDevices")) {
                    System.out.println();
                }
                xhtml.startElement("li", "class", "uuidlink");
                xhtml.characters("[Data was extracted as subitem]");
                xhtml.endElement("li");

                handleData(path, (NSData) nso, metadata, extractor);

            } catch (IOException | SAXException e) {
                e.printStackTrace();
            }
            return;
        }

        xhtml.startElement("details", "open", "true");
        if (nso instanceof NSArray) {
            if (((NSArray) nso).getArray().length > 0) {
                xhtml.startElement("summary", "class", "is-expandable");
                xhtml.characters("Array");
                xhtml.endElement("summary");
                for (NSObject ao : ((NSArray) nso).getArray()) {
                    parseNSObject(ao, xhtml, metadata, path, extractor, context);
                }
            }
        }
        if (nso instanceof NSSet) {
            if (((NSSet) nso).allObjects().length > 0) {
                xhtml.startElement("summary", "class", "is-expandable");
                xhtml.characters("Set");
                xhtml.endElement("summary");
                for (NSObject ao : ((NSSet) nso).allObjects()) {
                    parseNSObject(ao, xhtml, metadata, path, extractor, context);
                }
            }
        }
        if (nso instanceof NSDictionary) {
            if (((NSDictionary) nso).size() > 0) {
                xhtml.startElement("summary", "class", "is-expandable");
                xhtml.characters("Dict");
                xhtml.endElement("summary");
                for (Entry<String, NSObject> d : ((NSDictionary) nso).getHashMap().entrySet()) {
                    xhtml.startElement("details");
                    xhtml.startElement("summary", "class", "is-expandable");
                    xhtml.characters(d.getKey());
                    xhtml.endElement("summary");
                    parseNSObject(d.getValue(), xhtml, metadata, path + ":" + d.getKey(), extractor, context);
                    xhtml.endElement("details");

                }
            }
        }
        xhtml.endElement("details");
    }

    private void handleData(String path, NSData value, Metadata metadata, EmbeddedDocumentExtractor extractor) throws IOException, SAXException {
        if (!extractor.shouldParseEmbedded(metadata)) {
            return;
        }

        try (TikaInputStream tis = TikaInputStream.get(value.bytes())) {
            Metadata m2 = new Metadata();
            String name = path.substring(getBasePath().length() + 1);
            m2.add(BasicProps.NAME, name);
            m2.add(TikaCoreProperties.RESOURCE_NAME_KEY, name);

            extractor.parseEmbedded(tis, new IgnoreContentHandler(), m2, true);
        }
    }

    private static byte[] readResourceAsBytes(String resource) {
        byte[] result = null;
        try {
            result = IOUtil.loadInputStream(PListParser.class.getResourceAsStream(resource));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return result;
    }

}
