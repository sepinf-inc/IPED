package iped.parsers.plist;

import static iped.parsers.plist.PListHelper.DATE;
import static iped.parsers.plist.PListHelper.NUMBER;
import static iped.parsers.plist.PListHelper.STRING;
import static iped.parsers.plist.PListHelper.UID;
import static iped.parsers.plist.PListHelper.appendPath;
import static iped.parsers.plist.PListHelper.getUIDInteger;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.util.Date;
import java.util.Map.Entry;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.detect.apple.BPListDetector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.slf4j.Logger;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

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

import iped.data.IItemReader;
import iped.utils.DateUtil;

/**
 * Based on org.apache.tika.parser.apple.PListParser
 * 
 * @see https://github.com/apache/tika/blob/main/tika-parsers/tika-parsers-standard/tika-parsers-standard-modules/tika-parser-apple-module/src/main/java/org/apache/tika/parser/apple/PListParser.java
 */
public abstract class AbstractPListParser<T> implements Parser {

    private static final long serialVersionUID = 1L;

    protected static final String SUMMARY_SUFFIX = ": ";

    protected static final int MAX_BASE64_LENGTH_TO_PRINT = 40;

    protected static final String PLIST_META_PREFIX = "plist:";
    protected static final String PLIST_DATES_META = PLIST_META_PREFIX + "dates";
    protected static final String PLIST_POSSIBLE_DATES_META = PLIST_META_PREFIX + "possibleDates";

    protected class State {
        final XHTMLContentHandler xhtml;
        final Metadata metadata;
        final EmbeddedDocumentExtractor embeddedDocumentExtractor;
        final ParseContext context;
        T extra;

        public State(XHTMLContentHandler xhtml, Metadata metadata, EmbeddedDocumentExtractor embeddedDocumentExtractor, ParseContext context) {
            this.xhtml = xhtml;
            this.metadata = metadata;
            this.embeddedDocumentExtractor = embeddedDocumentExtractor;
            this.context = context;
        }
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        EmbeddedDocumentExtractor embeddedDocumentExtractor = EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
        NSObject rootObj = null;
        // if this already went through the PListDetector,
        // there should be an NSObject in the open container
        if (stream instanceof TikaInputStream) {
            rootObj = (NSObject) ((TikaInputStream) stream).getOpenContainer();
        }

        if (rootObj == null) {
            try {
                if (stream instanceof TikaInputStream && ((TikaInputStream) stream).hasFile()) {
                    rootObj = PropertyListParser.parse(((TikaInputStream) stream).getFile());
                } else {
                    rootObj = PropertyListParser.parse(stream);
                }
            } catch (PropertyListFormatException | ParseException | ParserConfigurationException e) {
                throw new TikaException("problem parsing root", e);
            }
        }
        String contentType = metadata.get(Metadata.CONTENT_TYPE);
        if (BPListDetector.PLIST.toString().equals(contentType)) {
            if (rootObj instanceof NSDictionary) {
                MediaType subtype = BPListDetector.detectXMLOnKeys(((NSDictionary) rootObj).keySet());
                metadata.set(Metadata.CONTENT_TYPE, subtype.toString());
            }
        }

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata) {
            @Override
            public void endElement(String name) throws SAXException {
                super.endElement(name);
                if ("summary".equals(name)) {
                    newline();
                }
            }
        };
        xhtml.startDocument();

        try {
            // Basic styling for details/summary
            xhtml.startElement("style");
            xhtml.characters( //
                    "details { border: 1px solid #ccc; padding: 5px; margin-bottom: 5px; }" + //
                    "body > details { margin-top: 20px; }" + //
                    "details details { margin-left: 12px; margin-top: 6px; }" + //
                    "details details > p { margin-left: 40px; }" + //
                    "summary { font-weight: bold; cursor: pointer; }" + //
                    "li { margin-bottom: 3px; }" + //
                    "p { margin: 2px 0 2px 20px; }" //
            );
            xhtml.endElement("style");

            IItemReader item = context.get(IItemReader.class);
            if (item != null) {
                xhtml.element("h3", item.getName());
            }

            State state = new State(xhtml, metadata, embeddedDocumentExtractor, context);
            processAndGenerateHTMLContent(rootObj, state);

        } catch (Exception e) {
            getLogger().error("Error processing plist", e);
            throw e;
        } finally {
            xhtml.endDocument();
        }
    }

    protected abstract Logger getLogger();

    protected abstract void processAndGenerateHTMLContent(NSObject obj, State state) throws SAXException, TikaException;

    protected AttributesImpl createAtributes(State state) {
        return new AttributesImpl();
    }

    protected void extractDataAsSubItem(NSData data, String path, State state) throws SAXException {
        if (state.embeddedDocumentExtractor.shouldParseEmbedded(state.metadata)) {
            Metadata entryMetadata = new Metadata();
            String name = String.format("PList-Data-[%s].dat", path);
            entryMetadata.add(TikaCoreProperties.RESOURCE_NAME_KEY, name);

            try (TikaInputStream tis = TikaInputStream.get(data.bytes())) {

                state.embeddedDocumentExtractor.parseEmbedded(tis, state.xhtml, entryMetadata, true);

            } catch (IOException e) {
                getLogger().warn("Error adding plist data as sub-item " + state.metadata + ">>" + path, e);
            }
        }
    }

    protected void processObject(NSObject obj, String path, State state, boolean open) throws SAXException {

        if (obj instanceof NSDictionary) {
            processDictionary((NSDictionary) obj, path, state, open);

        } else if (obj instanceof NSArray) {
            processArray((NSArray) obj, path, state);

        } else if (obj instanceof NSSet) {
            processSet((NSSet) obj, path, state);

        } else if (obj instanceof NSString) {
            processSimpleText(((NSString) obj).getContent(), STRING, state);

        } else if (obj instanceof NSNumber) {
            processNumber((NSNumber) obj, path, state);

        } else if (obj instanceof NSDate) {
            processDate(((NSDate) obj).getDate(), state);

        } else if (obj instanceof UID) {
            processUID((UID) obj, state);

        } else if (obj instanceof NSData) {
            processData((NSData) obj, path, state);

        } else {
            getLogger().warn("obj is not supported", obj);
        }
    }

    protected void processDictionary(NSDictionary obj, String path, State state, boolean open) throws SAXException {

        if (StringUtils.isEmpty(path)) {
            AttributesImpl attrs = createAtributes(state);
            if (open) {
                attrs.addAttribute("", "open", "", "", "true");
            }
            state.xhtml.startElement("details", attrs);
            state.xhtml.startElement("summary");
            processItemsCount(state, "Dictionary", obj.count());
            state.xhtml.endElement("summary");
        }

        for (Entry<String, NSObject> entry : obj.entrySet()) {
            String key = entry.getKey();
            NSObject value = entry.getValue();

            // Wrap the value in its own details/summary
            // For consistency, we can use details for all key-value pairs.
            state.xhtml.startElement("details");
            state.xhtml.startElement("summary");
            state.xhtml.startElement("strong");
            state.xhtml.characters(key + SUMMARY_SUFFIX);
            state.xhtml.endElement("strong");

            // Peek at value type for summary
            if (value instanceof NSArray) {
                processItemsCount(state, "array", ((NSArray) value).count());
            } else if (value instanceof NSSet) {
                processItemsCount(state, "set", ((NSSet) value).count());
            } else if (value instanceof NSDictionary) {
                processItemsCount(state, "dictionary", ((NSDictionary) value).count());
            }

            state.xhtml.endElement("summary");

            processObject(value, appendPath(path, key), state, false); // nested items are not open by default

            state.xhtml.endElement("details");
        }

        if (StringUtils.isEmpty(path)) {
            state.xhtml.endElement("details");
        }
    }

    protected void processItemsCount(State state, String type, int count) throws SAXException {
        state.xhtml.startElement("em");
        String prefix = StringUtils.isNotBlank(type) ? type + " - " : "";
        String text = count == 0 ? "empty" : count + " " + (count == 1 ? "item" : "items");
        state.xhtml.characters(" (" + prefix + text + ")");
        state.xhtml.endElement("em");
    }

    protected void processArray(NSArray obj, String path, State state) throws SAXException {

        state.xhtml.startElement("ol", createAtributes(state));

        for (NSObject member : obj.getArray()) {
            state.xhtml.startElement("li");
            processObject(member, path, state, false);
            state.xhtml.endElement("li");
        }

        state.xhtml.endElement("ol");
    }

    protected void processSet(NSSet obj, String path, State state) throws SAXException {

        state.xhtml.startElement("ul", createAtributes(state));

        for (NSObject member : obj.allObjects()) {
            state.xhtml.startElement("li");
            processObject(member, path, state, false);
            state.xhtml.endElement("li");
        }

        state.xhtml.endElement("ul");
    }

    protected void processSimpleText(String text, String type, State state) throws SAXException {
        state.xhtml.startElement("p", createAtributes(state));
        state.xhtml.startElement("em");
        state.xhtml.characters("(" + type + ")");
        state.xhtml.endElement("em");
        state.xhtml.characters(": " + text);
        state.xhtml.endElement("p");
    }

    protected void processNumber(NSNumber obj, String path, State state) throws SAXException {
        String text = obj.toString();
        processSimpleText(text, NUMBER, state);
        processPossibleDate(obj, path, state);
    }

    protected void processDate(Date date, State state) throws SAXException {
        processSimpleText(DateUtil.dateToString(date), DATE, state);
        state.metadata.add(PLIST_DATES_META, DateUtil.dateToString(date));
    }

    protected void processUID(UID obj, State state) throws SAXException {
        String text = Integer.toString(getUIDInteger(obj));
        processSimpleText(text, UID, state);
    }

    protected void processPossibleDate(NSNumber number, String path, State state) throws SAXException {

        Date possibleDate = PListHelper.getPossibleDate(number);

        if (possibleDate != null && StringUtils.containsAny(path, "Time", "Date", "Instant")) {
            // add possible date in the view
            state.xhtml.startElement("p");
            state.xhtml.startElement("em");
            state.xhtml.characters("(possible date)");
            state.xhtml.endElement("em");
            state.xhtml.characters(": " + DateUtil.dateToString(possibleDate));
            state.xhtml.endElement("p");

            // add possible date in metadata
            state.metadata.add(PLIST_POSSIBLE_DATES_META, DateUtil.dateToString(possibleDate));
        }
    }

    protected void processData(NSData value, String path, State state) throws SAXException {
        String dataText = value.getBase64EncodedData();
        String displayText = dataText;
        if (dataText.length() > MAX_BASE64_LENGTH_TO_PRINT) {
            displayText = dataText.substring(0, MAX_BASE64_LENGTH_TO_PRINT) + "...";
        }
        state.xhtml.startElement("p", createAtributes(state));
        state.xhtml.startElement("em");
        state.xhtml.characters("(data)");
        state.xhtml.endElement("em");
        state.xhtml.characters(": " + displayText + " (Base64 encoded, " + value.length() + " bytes)");
        state.xhtml.endElement("p");

        extractDataAsSubItem(value, path, state);
    }
}