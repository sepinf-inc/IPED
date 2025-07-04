package iped.parsers.plist;

import static iped.parsers.plist.PListHelper.METADATA_KEY_SEPARATOR;
import static iped.parsers.plist.PListHelper.appendPath;
import static iped.parsers.plist.PListHelper.getUIDInteger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSNumber;
import com.dd.plist.NSObject;
import com.dd.plist.NSString;
import com.dd.plist.UID;

import iped.data.IItemReader;
import iped.parsers.plist.detector.PListDetector;
import iped.parsers.standard.StandardParser;
import iped.properties.BasicProps;
import iped.utils.DateUtil;
import iped.utils.EmptyInputStream;

public class NSKeyedArchiverParser extends AbstractPListParser<NSKeyedArchiverParser.Extra> {

    private static final long serialVersionUID = -2634688360813722393L;

    private static final Logger logger = LoggerFactory.getLogger(NSKeyedArchiverParser.class);

    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(PListDetector.NSKEYEDARCHIVER_PLIST);
    private static final String JS_UID_ID_PREFIX = "uid-";

    private static final String ARCHIVER_KEY = "$archiver";
    private static final String VERSION_KEY = "$version";
    private static final String TOP_KEY = "$top";
    private static final String OBJECTS_KEY = "$objects";

    private static final String NULL = "$null";
    private static final String CLASS = "$class";
    private static final String CLASSNAME = "$classname";
    private static final String NS_TIME = "NS.time";
    private static final String NS_OBJECTS = "NS.objects";
    private static final String NS_KEYS = "NS.keys";

    public static final MediaType APPLE_LOCATION = MediaType.application("x-apple-location");

    private static final String PLIST_LOC_META_PREFIX = PLIST_META_PREFIX + "loc:";

    private static final String CL_LOCATION = "CLLocation";
    private static final String CL_LOCATION_PREFIX = "kCLLocationCodingKey";
    private static final String CL_LOCATION_RAW_PREFIX = CL_LOCATION_PREFIX + "Raw";
    private static final String CL_LOCATION_LONGITUDE = CL_LOCATION_PREFIX + "CoordinateLongitude";
    private static final String CL_LOCATION_LATITUDE = CL_LOCATION_PREFIX + "CoordinateLatitude";
    private static final String CL_LOCATION_ALTITUDE = CL_LOCATION_PREFIX + "Altitude";
    private static final String CL_LOCATION_TIMESTAMP = CL_LOCATION_PREFIX + "Timestamp";

    private static String jsContent;

    public class Extra {
        NSArray objects;
        Set<Integer> alreadyVisitedUIDs;
        UID currentUID;

        public Extra(NSArray objects, Set<Integer> alreadyVisitedUIDs) {
            this.objects = objects;
            this.alreadyVisitedUIDs = alreadyVisitedUIDs;
        }
    }

    private static synchronized String getJSContent() throws IOException {
        if (jsContent == null) {
            jsContent = IOUtils.resourceToString("/iped/parsers/common/jump.js", StandardCharsets.UTF_8);
        }
        return jsContent;
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    protected Logger getLogger() {
        return logger;
    }

    @Override
    protected AttributesImpl createAtributes(State state) {
        AttributesImpl attr = new AttributesImpl();
        if (state.extra.currentUID != null) {
            attr.addAttribute("", "id", "", "", JS_UID_ID_PREFIX + getUIDInteger(state.extra.currentUID));
        }
        return attr;
    }

    @Override
    protected void processAndGenerateHTMLContent(NSObject nso, State state) throws SAXException, TikaException {

        if (!(nso instanceof NSDictionary) || !PListDetector.isNSKeyedArchiver((NSDictionary) nso)) {
            logger.error("NSKeyedArchiver is unexpectedly invalid: {}", state.context.get(IItemReader.class));
            processAsPList(nso, state);
            return;
        }

        HashMap<String, NSObject> map = ((NSDictionary) nso).getHashMap();
        NSObject archiver = map.get(ARCHIVER_KEY);
        NSObject version = map.get(VERSION_KEY);
        NSObject top = map.get(TOP_KEY);
        NSObject objects = map.get(OBJECTS_KEY);

        if (!(objects instanceof NSArray) || !((top instanceof NSDictionary))) {
            logger.warn("NSKeyedArchiver has no valid $object or $top: {}", state.context.get(IItemReader.class));
            processAsPList(nso, state);
            return;
        }

        if (((NSDictionary) top).size() == 0) {
            logger.warn("NSKeyedArchiver has no $top element: {}", state.context.get(IItemReader.class));
            processAsPList(nso, state);
            return;
        }

        state.xhtml.startElement("p");
        state.xhtml.characters("Archiver: " + archiver + ", Version: " + (version != null ? version : "N/A") //
                + ", Objects count: " + ((NSArray) objects).count());
        state.xhtml.endElement("p");

        try {
            // process NSKeyedArchiver
            Set<Integer> alreadyVisitedUIDs = new HashSet<>(); // array to control already written objects and avoid infinite loops
            state.extra = new Extra((NSArray) objects, alreadyVisitedUIDs);
            processTop((NSDictionary) top, state);

            // add JS
            state.xhtml.startElement("script");
            state.xhtml.characters(getJSContent());
            state.xhtml.endElement("script");

        } catch (IOException e) {
            logger.error("Error reading JS", e);
        } catch (Exception e) {
            logger.error("Error parsing object", e);
            throw e;
        }
    }

    public boolean isPrimitive(NSObject obj) {
        return !(obj instanceof NSDictionary) && !(obj instanceof NSArray);
    }

    public boolean isNSTime(NSObject obj) {
        return (obj instanceof NSDictionary) //
                && ((NSDictionary) obj).containsKey(NS_TIME);
    }

    public boolean isKADictionary(NSObject obj) {
        return (obj instanceof NSDictionary) //
                && ((NSDictionary) obj).containsKey(NS_KEYS) //
                && (((NSDictionary) obj).containsKey(NS_OBJECTS));
    }

    public boolean isKAArray(NSObject obj) {
        return (obj instanceof NSDictionary) //
                && ((NSDictionary) obj).containsKey(CLASS) //
                && !((NSDictionary) obj).containsKey(NS_KEYS) //
                && ((NSDictionary) obj).containsKey(NS_OBJECTS);
    }

    public boolean isKALocation(NSObject obj, String path) {
        return (obj instanceof NSDictionary) //
                && path.endsWith(CL_LOCATION) //
                && ((NSDictionary) obj).containsKey(CL_LOCATION_LATITUDE) //
                && ((NSDictionary) obj).containsKey(CL_LOCATION_LONGITUDE);
    }

    public String getNSClassName(NSDictionary obj, NSArray objects) {
        try {
            int uidInt = getUIDInteger((UID) obj.get(CLASS));
            NSDictionary classObj = (NSDictionary) objects.getArray()[uidInt];
            return classObj.get(CLASSNAME).toString();
        } catch (Exception e) {
            return obj.getClass().getSimpleName();
        }
    }

    public void processAsPList(NSObject nso, State state) throws SAXException {
        PListParser plistParser = new PListParser();
        AbstractPListParser<Void>.State plistState = plistParser.new State(state.xhtml, state.metadata, state.embeddedDocumentExtractor, state.context);
        plistParser.processObject(nso, "", plistState, true);
    }

    public void processTop(NSDictionary top, State state) throws SAXException {
        for (Entry<String, NSObject> e : top.entrySet()) {
            state.xhtml.startElement("details", "open", "true");
            state.xhtml.startElement("summary");
            state.xhtml.characters(e.getKey());
            state.xhtml.endElement("summary");
            processObject(e.getValue(), e.getKey(), state, false);
            state.xhtml.endElement("details");
        }
    }

    @Override
    protected void processObject(NSObject obj, String path, State state, boolean open) throws SAXException {

        // replace obj in case it is UID
        state.extra.currentUID = null;
        if (obj instanceof UID) {
            UID uid = (UID) obj;
            int uidInt = getUIDInteger(uid);

            // checks if object was already written to avoid infinite loops
            if (state.extra.alreadyVisitedUIDs.contains(uidInt)) {
                processVisitedObject(uidInt, state);
                return;
            } else {
                state.extra.currentUID = uid;
                obj = state.extra.objects.objectAtIndex(uidInt);
                state.extra.alreadyVisitedUIDs.add(uidInt);
            }
        }

        if (isPrimitive(obj)) {
            super.processObject(obj, path, state, false);
        } else {

            if (isNSTime(obj)) {
                processNSTime((NSDictionary) obj, state);

            } else if (isKALocation(obj, path)) {
                processKALocation((NSDictionary) obj, path, state);

            } else if (isKAArray(obj)) {
                processKAArray((NSDictionary) obj, path, state);

            } else if (isKADictionary(obj)) {
                processKADictionary((NSDictionary) obj, path, state);

            } else if (obj instanceof NSArray || obj instanceof NSDictionary) {
                super.processObject(obj, path, state, open);

            } else {
                logger.error("Unexpected object: {}", obj);
            }
        }
    }

    @Override
    protected void processDictionary(NSDictionary obj, String path, AbstractPListParser<Extra>.State state, boolean open) throws SAXException {

        if (obj.containsKey(CLASS)) {

            String className = getNSClassName(obj, state.extra.objects);
            NSDictionary newDict = obj.clone();
            newDict.remove(CLASS);

            state.xhtml.startElement("details", createAtributes(state));
            state.xhtml.startElement("summary");
            state.xhtml.characters(className + SUMMARY_SUFFIX);
            processItemsCount(state, "dictionary", newDict.size());
            state.xhtml.endElement("summary");

            if (!obj.isEmpty()) {
                processObject(newDict, appendPath(path, className), state, open);
            }

            state.xhtml.endElement("details");
        } else {
            super.processDictionary(obj, path, state, open);
        }
    }

    private void processVisitedObject(Integer uidInt, State state) throws SAXException {
        state.xhtml.startElement("p");
        AttributesImpl attrs = new AttributesImpl();
        attrs.addAttribute("", "href", "", "", "#");
        attrs.addAttribute("", "onclick", "", "", "jumpTo('#" + JS_UID_ID_PREFIX + uidInt + "')");
        state.xhtml.startElement("a", attrs);
        state.xhtml.characters("[Object link to UID " + uidInt + "]");
        state.xhtml.endElement("a");

        state.xhtml.endElement("p");
    }

    private void processNSTime(NSDictionary obj, State state) throws SAXException {
        NSObject nso = obj.get(NS_TIME);
        Date date = PListHelper.getNSTimeDate((NSNumber) nso);
        processDate(date, state);
    }

    private void processKALocation(NSDictionary obj, String path, State state) throws SAXException {

        processDictionary(obj, path, state, false);

        if (state.embeddedDocumentExtractor.shouldParseEmbedded(state.metadata)) {
            try {
                Metadata metadata = new Metadata();
                String name = String.format("NSKeyArchiver-Location-[%s]", path);
                metadata.add(TikaCoreProperties.RESOURCE_NAME_KEY, name);
                metadata.add(TikaCoreProperties.TITLE, name);
                metadata.add(StandardParser.INDEXER_CONTENT_TYPE, APPLE_LOCATION.toString());
                metadata.set(BasicProps.LENGTH, "");

                metadata.set(Metadata.LATITUDE, obj.get(CL_LOCATION_LATITUDE).toString());
                metadata.set(Metadata.LONGITUDE, obj.get(CL_LOCATION_LONGITUDE).toString());
                if (obj.containsKey(CL_LOCATION_ALTITUDE)) {
                    metadata.set(Metadata.ALTITUDE, obj.get(CL_LOCATION_ALTITUDE).toString());
                }

                for (Entry<String, NSObject> entry : obj.entrySet()) {
                    NSObject value = entry.getValue();
                    String key = entry.getKey();
                    if (value == null //
                            || !StringUtils.startsWith(key, CL_LOCATION_PREFIX) //
                            || StringUtils.equalsAny(key, CL_LOCATION_LATITUDE, //
                                    CL_LOCATION_LONGITUDE, //
                                    CL_LOCATION_ALTITUDE, //
                                    CL_LOCATION_PREFIX + "SignalEnvironmentType") //
                            || StringUtils.startsWith(key, CL_LOCATION_RAW_PREFIX)) {
                        continue;
                    }

                    String metadataKey = StringUtils.removeStart(key, CL_LOCATION_PREFIX);
                    metadataKey = StringUtils.uncapitalize(metadataKey);

                    if (value instanceof UID) {
                        value = state.extra.objects.objectAtIndex(getUIDInteger((UID) value));
                    }
                    String valueStr = null;
                    if (value instanceof NSNumber) {
                        NSNumber number = (NSNumber) value;
                        if (CL_LOCATION_TIMESTAMP.equals(key)) {
                            Date timestamp = PListHelper.getNSTimeDate((NSNumber) value);
                            if (timestamp != null) {
                                valueStr = DateUtil.dateToString(timestamp);
                            }
                        } else if (number.isInteger() && number.intValue() >= 0 && number.intValue() != Integer.MAX_VALUE //
                                || number.isReal() && number.doubleValue() >= 0 //
                                || number.isBoolean()) {
                            valueStr = value.toString();
                        }
                    } else if (value instanceof NSString) {
                        valueStr = value.toString();
                    }

                    if (valueStr != null && !NULL.equals(valueStr)) {
                        metadata.set(PLIST_LOC_META_PREFIX + metadataKey, valueStr);
                    }
                }

                state.embeddedDocumentExtractor.parseEmbedded(new EmptyInputStream(), state.xhtml, metadata, true);
            } catch (IOException e) {
                getLogger().warn("Error adding plist data as sub-item " + state.metadata + ">>" + path, e);
            }
        }
    }

    private void processKAArray(NSDictionary obj, String path, State state) throws SAXException {

        NSArray array = (NSArray) obj.get(NS_OBJECTS);
        String className = getNSClassName(obj, state.extra.objects);

        state.xhtml.startElement("details", createAtributes(state));
        state.xhtml.startElement("summary");
        state.xhtml.characters(className + SUMMARY_SUFFIX);
        processItemsCount(state, "Keyed Archive array", array.count());
        state.xhtml.endElement("summary");

        processArray(array, appendPath(path, className), state);

        state.xhtml.endElement("details");
    }

    private void processKADictionary(NSDictionary obj, String path, State state) throws SAXException {

        state.xhtml.startElement("details", createAtributes(state));
        state.xhtml.startElement("summary");
        String className = getNSClassName(obj, state.extra.objects);
        state.xhtml.characters(className + SUMMARY_SUFFIX);

        NSArray keys = (NSArray) obj.get(NS_KEYS); // possibly an array of UIDs
        NSArray objects = (NSArray) obj.get(NS_OBJECTS);

        // convert keys from UID to any
        NSArray indexedKeys = new NSArray(keys.count());
        for (int i = 0; i < keys.getArray().length; i++) {
            NSObject key = keys.getArray()[i];
            if (key instanceof UID) {
                int uidInt = getUIDInteger((UID) key);
                key = state.extra.objects.objectAtIndex(uidInt);
            }
            indexedKeys.getArray()[i] = key;
        }
        boolean allKeysAreValid = Arrays.asList(indexedKeys.getArray()).stream() //
                .allMatch(k -> k instanceof NSString || k instanceof NSNumber);

        if (indexedKeys.count() == objects.count() && allKeysAreValid) {
            int count = indexedKeys.count();
            processItemsCount(state, "Keyed Archive dictionary", count);
            state.xhtml.endElement("summary");

            // build and process equivalent dictionary
            if (count > 0) {
                NSDictionary newDict = new NSDictionary();
                for (int i = 0; i < count; i++) {
                    String key = indexedKeys.getArray()[i].toString();
                    newDict.put(key, objects.getArray()[i]);
                }
                processObject(newDict, appendPath(path, className), state, false);
            }

        } else {
            state.xhtml.endElement("summary");

            state.xhtml.startElement("details");
            state.xhtml.startElement("summary");
            state.xhtml.characters(NS_KEYS + SUMMARY_SUFFIX);
            processItemsCount(state, "", keys.count());
            state.xhtml.endElement("summary");
            processArray(keys, appendPath(path, className + METADATA_KEY_SEPARATOR + NS_KEYS), state);
            state.xhtml.endElement("details");

            state.xhtml.startElement("details");
            state.xhtml.startElement("summary");
            state.xhtml.characters(NS_OBJECTS + SUMMARY_SUFFIX);
            processItemsCount(state, "", objects.count());
            state.xhtml.endElement("summary");
            processArray(objects, appendPath(path, className + METADATA_KEY_SEPARATOR + NS_OBJECTS), state);
            state.xhtml.endElement("details");
        }

        state.xhtml.endElement("details");
    }
}
