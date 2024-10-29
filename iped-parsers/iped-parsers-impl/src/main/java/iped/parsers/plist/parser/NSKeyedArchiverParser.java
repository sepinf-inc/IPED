package iped.parsers.plist.parser;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import com.dd.plist.NSArray;
import com.dd.plist.NSDictionary;
import com.dd.plist.NSNumber;
import com.dd.plist.NSObject;
import com.dd.plist.UID;

import iped.parsers.plist.detector.PListDetector;
import iped.parsers.util.MetadataUtil;
import iped.utils.DateUtil;
import iped.utils.IOUtil;

public class NSKeyedArchiverParser extends PListParser {
    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(PListDetector.NSKEYEDARCHIVER_PLIST);
    private static final String NSKEYEDARCHIVER_METADATA_SUFFIX = "nskeyedarchiver";

    private static final Object ARCHIVERKEY = "$archiver";
    private static final Object TOPKEY = "$top";
    private static final Object OBJECTSKEY = "$objects";

    private static final String CSS = new String(readResourceAsBytes("/iped/parsers/css/treeview.css"), Charset.forName("UTF-8"));
    private static final String UUIDJS = new String(readResourceAsBytes("/iped/parsers/css/uuidlink.js"), Charset.forName("UTF-8"));

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext arg0) {
        return SUPPORTED_TYPES;
    }

    public void parseNSObject(NSObject nso, XHTMLContentHandler xhtml, Metadata metadata, String path, EmbeddedDocumentExtractor extractor, ParseContext context) throws SAXException {
        if (nso instanceof NSDictionary) {
            HashMap<String, NSObject> map = ((NSDictionary) nso).getHashMap();

            NSObject top = map.get(TOPKEY);
            NSObject objects = map.get(OBJECTSKEY);

            if (top != null && objects != null) {
                NSObject root = null;
                int rootIndex = 0;
                HashMap<Integer, Object> objectsMap = new HashMap<Integer, Object>();

                NSObject rootObj = null;
                if (top instanceof NSDictionary) {
                    if (((NSDictionary) top).size() > 0) {
                        root = ((NSDictionary) top).get("root");
                    }
                }

                if (root != null) {
                    Set<NSObject> alreadyVisitedObjects = new HashSet<>();// array to control already written objects and avoid infinite loops
                    parseObject("", root, ((NSArray) objects), alreadyVisitedObjects, xhtml, metadata, getBasePath(), extractor, context);
                } else {
                    // try to parse as a PList file
                    parseAsPList(nso, xhtml, metadata, path, extractor, context);
                }
            } else {
                // try to parse as a PList file
                parseAsPList(nso, xhtml, metadata, path, extractor, context);
            }
        } else {
            // try to parse as a PList file
            parseAsPList(nso, xhtml, metadata, path, extractor, context);
        }
    }

    public void parseAsPList(NSObject nso, XHTMLContentHandler xhtml, Metadata metadata, String path, EmbeddedDocumentExtractor extractor, ParseContext context) throws SAXException {
        super.parseNSObject(nso, xhtml, metadata, path, extractor, context);
    }

    public int getUUIDInteger(UID uid) {
        byte[] b = new byte[4];
        byte[] b2 = uid.getBytes();
        for (int i = b.length - 1, j = b2.length - 1; i >= 0 && j >= 0; i--, j--) {
            b[i] = b2[j];
        }
        ByteBuffer wrapped = ByteBuffer.wrap(b);
        return wrapped.getInt();
    }

    public boolean isPrimitive(NSObject obj) {
        return !(obj instanceof NSDictionary) && !(obj instanceof NSArray);
    }

    public boolean isNSTime(NSObject obj) {
        return ((obj instanceof NSDictionary) && (((NSDictionary) obj).containsKey("NS.time")));
    }

    /*
     * Dictionary objects was found in different representations. One with the keys
     * and objects referenced by an UID array, and the other as a common BPList
     * dictionary (function isNSDictionary).
     */
    public boolean isKADictionary(NSObject obj) {
        return ((obj instanceof NSDictionary) && (((NSDictionary) obj).containsKey("NS.keys")) && (((NSDictionary) obj).containsKey("NS.objects")));
    }

    public boolean isNSDictionary(NSObject obj) {
        return ((obj instanceof NSDictionary) && (!((NSDictionary) obj).containsKey("NS.objects")) && !(((NSDictionary) obj).containsKey("NS.keys")));
    }

    public boolean isKAArray(NSObject obj) {
        return ((obj instanceof NSDictionary) && ((NSDictionary) obj).containsKey("$class") && !((NSDictionary) obj).containsKey("NS.keys") && (((NSDictionary) obj).containsKey("NS.objects")));
    }

    private void parseObject(String name, NSObject object, NSArray objects, Set<NSObject> alreadyVisitedObjects, XHTMLContentHandler xhtml, Metadata metadata, String path,
            EmbeddedDocumentExtractor extractor,
            ParseContext context) throws SAXException {
        try {
            NSObject obj = object;
            UID uid = null;
            if(obj instanceof UID) {
                uid = (UID) object;
                obj = objects.objectAtIndex(getUUIDInteger(uid));
            }

            if (isPrimitive(obj)) {
                parseNSPrimitiveObject(obj, xhtml, metadata, path, extractor, context);
            } else {
                // checks if object was already written to avoid infinite loops
                if (alreadyVisitedObjects.contains(obj)) {
                    xhtml.startElement("details", "open", "true");
                    xhtml.startElement("summary", "class", "is-expandable");
                    xhtml.startElement("a", "onclick", "clickUID(" + getUUIDInteger(uid) + ");");
                    xhtml.characters("[Object link]");
                    xhtml.endElement("a");
                    xhtml.endElement("summary");
                    xhtml.endElement("details");
                    return;
                } else {
                    alreadyVisitedObjects.add(obj);
                }

                AttributesImpl attr = new AttributesImpl();
                if (uid != null) {
                    attr.addAttribute("", "treeuid", "", "", Integer.toString(getUUIDInteger(uid)));
                    attr.addAttribute("", "class", "", "", "uidref");
                }

                if (isNSTime(obj)) {
                    parseNSTime(((NSDictionary) obj).get("NS.time"), xhtml, metadata, path, extractor, context);
                } else if (isKAArray(obj)) {
                    NSObject[] arrayObjects = ((NSArray) ((NSDictionary) obj).get("NS.objects")).getArray();
                    if (arrayObjects.length > 0) {
                        attr.addAttribute("", "open", "", "", "true");
                        xhtml.startElement("details", attr);
                        xhtml.startElement("summary", "class", "is-expandable");
                        xhtml.characters("[ARRAY]");
                        xhtml.endElement("summary");
                        String classname = getNSClassName(obj, objects);
                        for (NSObject member : arrayObjects) {
                            parseObject(classname, member, objects, alreadyVisitedObjects, xhtml, metadata, path, extractor, context);
                        }
                        xhtml.endElement("details");
                    }
                } else if (obj instanceof NSArray) {
                    attr.addAttribute("", "open", "", "", "true");
                    xhtml.startElement("details", attr);
                    xhtml.startElement("summary", "class", "is-expandable");
                    xhtml.characters(name);
                    xhtml.endElement("summary");
                    for (NSObject member : ((NSArray) obj).getArray()) {
                        parseObject(name, member, objects, alreadyVisitedObjects, xhtml, metadata, path, extractor, context);
                    }
                    xhtml.endElement("details");
                } else if (isNSDictionary(obj)) {
                    if (((NSDictionary) obj).size() > 0) {
                        xhtml.startElement("details", attr);
                        HashMap<String, NSObject> dictMap = ((NSDictionary) obj).getHashMap();
                        String className = getNSClassName(obj, objects);
                        xhtml.startElement("summary", "class", "is-expandable");
                        xhtml.characters(className == null ? "Dict" : className);
                        xhtml.endElement("summary");
                        for (Entry<String, NSObject> d : ((NSDictionary) obj).getHashMap().entrySet()) {
                            if (!"$class".equals(d.getKey())) {
                                xhtml.startElement("details");
                                xhtml.startElement("summary", "class", "is-expandable");
                                xhtml.characters(d.getKey());
                                xhtml.endElement("summary");
                                parseObject(d.getKey(), d.getValue(), objects, alreadyVisitedObjects, xhtml, metadata, path + ":" + className + ":" + d.getKey(), extractor,
                                        context);
                                xhtml.endElement("details");
                            }
                        }
                        xhtml.endElement("details");
                    }
                } else if (isKADictionary(obj)) {
                    parseKADictionary(name, uid, obj, objects, alreadyVisitedObjects, xhtml, metadata, path, extractor, context);
                } else {
                    xhtml.startElement("details", attr);
                    xhtml.startElement("summary", "class", "is-expandable");
                    String classname = getNSClassName(obj, objects);
                    xhtml.characters(classname);
                    xhtml.endElement("summary");
                    for (Entry<String, NSObject> e : ((NSDictionary) obj).entrySet()) {
                        if (!"$class".equals(e.getKey())) {
                            xhtml.startElement("details", "open", "true");
                            xhtml.startElement("summary", "class", "is-expandable");
                            xhtml.characters(e.getKey());
                            xhtml.endElement("summary");
                            parseObject(e.getKey(), e.getValue(), objects, alreadyVisitedObjects, xhtml, metadata, path + ":" + classname + ":" + e.getKey(), extractor, context);
                            xhtml.endElement("details");
                        }
                    }
                    xhtml.endElement("details");
                }
            }
        } catch (Exception e) {
            System.out.println();
            e.printStackTrace();
        }
    }

    private void parseNSTime(NSObject nso, XHTMLContentHandler xhtml, Metadata metadata, String path, EmbeddedDocumentExtractor extractor, ParseContext context) throws SAXException {
        Date date = new Date((((NSNumber) nso).longValue() + 978307200) * 1000);
        xhtml.startElement("li", "class", "nochild");
        xhtml.characters(nso.toString() + "(" + DateUtil.dateToString(date) + ")");
        xhtml.endElement("li");
        MetadataUtil.setMetadataType(path, Date.class);
        String dateStr = DateUtil.dateToString(date);
        metadata.add(path, dateStr);
    }

    public void parseKADictionary(String name, UID uid, NSObject obj, NSArray objects, Set<NSObject> alreadyVisitedObjects, XHTMLContentHandler xhtml, Metadata metadata, String path,
            EmbeddedDocumentExtractor extractor,
            ParseContext context) throws SAXException {

        AttributesImpl attr = new AttributesImpl();
        if (uid != null) {
            attr.addAttribute("", "treeuid", "", "", Integer.toString(getUUIDInteger(uid)));
            attr.addAttribute("", "class", "", "", "uidref");
        }
        attr.addAttribute("", "open", "", "", "true");

        xhtml.startElement("details", attr);
        xhtml.startElement("summary", "class", "is-expandable");
        String classname = getNSClassName(obj, objects);
        xhtml.characters(classname);
        xhtml.endElement("summary");

        xhtml.startElement("details", "open", "true");
        xhtml.startElement("summary", "class", "is-expandable");
        xhtml.characters("NS.keys");
        xhtml.endElement("summary");
        NSArray keys = (NSArray) ((NSDictionary) obj).get("NS.keys");
        int classIndex = -1;
        for (NSObject member : keys.getArray()) {
            parseObject(name, member, objects, alreadyVisitedObjects, xhtml, metadata, path, extractor, context);
        }
        xhtml.endElement("details");

        xhtml.startElement("details", "open", "true");
        xhtml.startElement("summary", "class", "is-expandable");
        xhtml.characters("NS.objects");
        xhtml.endElement("summary");
        NSArray objectsArray = (NSArray) ((NSDictionary) obj).get("NS.objects");
        int i=0;
        for (NSObject member : objectsArray.getArray()) {
            parseObject(name, member, objects, alreadyVisitedObjects, xhtml, metadata, path + ":" + classname, extractor, context);
            i++;
        }
        xhtml.endElement("details");
        xhtml.endElement("details");
    }

    public String getNSClassName(NSObject obj, NSArray objects) {
        try {
            NSDictionary classObj = (NSDictionary) objects.getArray()[getUUIDInteger((UID) ((NSDictionary) obj).get("$class"))];
            return classObj.get("$classname").toString();
        } catch (Exception e) {
            return obj.getClass().getName();
        }
    }

    static public boolean isNSKeyedArchiver(NSObject oc) {
        if(oc instanceof NSDictionary) {
            HashMap<String, NSObject> map = ((NSDictionary)oc).getHashMap();
            NSObject archiver = map.get(ARCHIVERKEY);
            // NSObject top = map.get(TOPKEY);
            if (archiver != null && archiver.toString().equals("NSKeyedArchiver")) {
                return true;
            }
        }
        return false;
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

    @Override
    public String getBasePath() {
        return NSKEYEDARCHIVER_METADATA_SUFFIX;
    }

    @Override
    public void parseUID(UID nso, XHTMLContentHandler xhtml, Metadata metadata, String path, EmbeddedDocumentExtractor extractor, ParseContext context) throws SAXException {
        xhtml.startElement("li", "class", "nochild");
        xhtml.characters(((UID) nso).getName() + "(" + getUUIDInteger(nso) + ")");
        xhtml.endElement("li");
    }

}
