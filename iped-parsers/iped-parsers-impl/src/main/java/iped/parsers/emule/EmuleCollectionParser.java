package iped.parsers.emule;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import iped.parsers.emule.data.ECollection;
import iped.parsers.emule.data.ECollectionFile;
import iped.parsers.emule.data.ED2KURLCollection;
import iped.parsers.emule.data.ED2KURLCollectionFile;
import iped.parsers.emule.data.EmuleCollection;
import iped.parsers.util.ChildPornHashLookup;
import iped.parsers.util.Messages;
import iped.properties.ExtraProperties;

/*
 * Emule collections are files with collections of links to emule urls to download.
 * 
 */

public class EmuleCollectionParser extends AbstractParser {
    public static final String EMULE_COLLECTION_MIME_TYPE = "application/x-emule-collection"; //$NON-NLS-1$
    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(MediaType.parse(EMULE_COLLECTION_MIME_TYPE));
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024;

    static String ED2K_URL_REGEX = "(ed2k://)\\|file\\|(?<filename>[^\\|]*)\\|(?<size>[^\\|]*)\\|(?<hash>[^\\|]*)\\|";

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext arg0) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {
        final DateFormat df = new SimpleDateFormat(Messages.getString("KnownMetParser.DataFormat")); //$NON-NLS-1$
        df.setTimeZone(TimeZone.getTimeZone("GMT+0")); //$NON-NLS-1$

        TemporaryResources tmp = new TemporaryResources();
        try {
            TikaInputStream tikaStream = TikaInputStream.get(stream, tmp);
            long size = tikaStream.getLength();
            if (size > MAX_FILE_SIZE)
                throw new TikaException("EmuleCollection is too big (" + size + " bytes): maximum supported size to parse is " + MAX_FILE_SIZE + " bytes");
            ByteBuffer bb = ByteBuffer.wrap(tikaStream.readAllBytes());
            ECollection collection = null;
            try {
                collection = EmuleCollection.loadCollectionFile(bb);
                List<ECollectionFile> files = collection.getFiles();
                if (files.size() <= 0) {
                    collection = null;
                }
            } catch (Exception e) {
                collection = null;
            }

            if (collection == null) {// tries to find ed2k urls with regex

                collection = new ED2KURLCollection(metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY));
                List<ECollectionFile> files = collection.getFiles();

                Pattern p = Pattern.compile(ED2K_URL_REGEX);
                Scanner sc = new Scanner(new ByteArrayInputStream(bb.array()), "UTF-8");
                StringBuilder input = new StringBuilder();
                while (sc.hasNextLine()) {
                    input.append(sc.nextLine());
                }

                Matcher m = p.matcher(input);
                while (m.find()) {
                    files.add(new ED2KURLCollectionFile(m.group("filename"), m.group("hash"), m.group("size")));
                }

                if (files.size() <= 0) {
                    collection = null;
                }
            }
            if (collection != null) {
                parse(collection, handler, metadata, context);
            }

        } finally {
            tmp.dispose();
        }

    }

    public void parse(ECollection collection, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {
        metadata.set(HttpHeaders.CONTENT_TYPE, EMULE_COLLECTION_MIME_TYPE);
        metadata.remove(TikaCoreProperties.RESOURCE_NAME_KEY);

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        xhtml.startElement("style"); //$NON-NLS-1$
        xhtml.characters(".dt {border-collapse: collapse; border-style: solid; font-family: Arial, sans-serif; width: 1800px; margin-right: 32px; margin-bottom: 32px; width: 100%;  } " //$NON-NLS-1$
                + ".rh { font-weight: bold; text-align: center; background-color:#AAAAEE; vertical-align: middle; word-wrap: break-word;} " //$NON-NLS-1$
                + ".ra { vertical-align: middle; } " //$NON-NLS-1$
                + ".rb { background-color:#E7E7F0; vertical-align: middle; } " //$NON-NLS-1$
                + ".rr { background-color:#E77770; vertical-align: middle; } " //$NON-NLS-1$
                + ".hashtd { text-align:center; } " //$NON-NLS-1$
                + ".sizetd { text-align:right; } " //$NON-NLS-1$
                + "th { border-style: solid; border-width:1px; width:fit-content;}" //$NON-NLS-1$
                + "td { border-style: solid; border-width:1px; width:fit-content;}" //$NON-NLS-1$
                + ".nametd { word-wrap: break-word; width:70% }" //$NON-NLS-1$
                + ".s { border: solid; border-width: thin; padding: 3px; text-align: center; vertical-align: middle; word-wrap: break-word; width: 80px; } " //$NON-NLS-1$
                + ".e { border: solid; border-width: thin; padding: 3px; text-align: center; vertical-align: middle; word-wrap: break-word; width: 140px; font-family: monospace; } " //$NON-NLS-1$
                + ".a { border: solid; border-width: thin; padding: 3px; text-align: center; vertical-align: middle; word-wrap: break-word; width: 110px; } " //$NON-NLS-1$
                + ".b { border: solid; border-width: thin; padding: 3px; text-align: left; vertical-align: middle; word-wrap: break-word; word-break: break-all; width: 420px; } " //$NON-NLS-1$
                + ".c { border: solid; border-width: thin; padding: 3px; text-align: right; vertical-align: middle; word-wrap: break-word;  width: 110px; }"); //$NON-NLS-1$
        xhtml.endElement("style"); //$NON-NLS-1$
        xhtml.newline();

        xhtml.startElement("p");
        xhtml.characters(collection.getName());
        xhtml.endElement("p");
        xhtml.newline();

        List<ECollectionFile> files = collection.getFiles();
        xhtml.startElement("table", "class", "dt"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        xhtml.startElement("tr"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute("", "class", "class", "CDATA", "rh");
        xhtml.startElement("th", attributes); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        xhtml.characters(Messages.getString("KnownMetParser.Seq"));
        xhtml.endElement("th"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        attributes = new AttributesImpl();
        attributes.addAttribute("", "class", "class", "CDATA", "rh");
        xhtml.startElement("th", attributes); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        xhtml.characters(Messages.getString("KnownMetParser.Name"));
        xhtml.endElement("th"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        attributes = new AttributesImpl();
        attributes.addAttribute("", "class", "class", "CDATA", "rh");
        xhtml.startElement("th", attributes); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        xhtml.characters(Messages.getString("KnownMetParser.Hash"));
        xhtml.endElement("th"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        attributes = new AttributesImpl();
        attributes.addAttribute("", "class", "class", "CDATA", "rh");
        xhtml.startElement("th", attributes); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        xhtml.characters(Messages.getString("KnownMetParser.Size"));
        xhtml.endElement("th"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

        xhtml.endElement("tr"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$


        int i = 0;
        String linkedItens = "";

        long totalSize = 0;
        for (ECollectionFile cf : files) {
            String trClass = "ra";

            String hashStr = cf.getHashStr();
            List<String> hashset = null;
            if (hashStr != null && hashStr.length() == 32) {
                linkedItens += " || " + KnownMetParser.EDONKEY + ":" + hashStr;


                hashset = ChildPornHashLookup.lookupHash(KnownMetParser.EDONKEY, hashStr);
            }

            if (hashset != null && hashset.size() > 0) {
                trClass = "rr";
            }
            attributes = new AttributesImpl();
            attributes.addAttribute("", "class", "class", "CDATA", trClass);
            xhtml.startElement("tr", attributes); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

            xhtml.startElement("td"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            xhtml.characters(Integer.toString(i + 1));
            xhtml.endElement("td"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

            attributes = new AttributesImpl();
            attributes.addAttribute("", "class", "class", "CDATA", "nametd");
            xhtml.startElement("td", attributes); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            xhtml.characters(cf.getName());
            xhtml.endElement("td"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

            attributes = new AttributesImpl();
            attributes.addAttribute("", "class", "class", "CDATA", "hashtd");
            xhtml.startElement("td", attributes); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            xhtml.characters(cf.getHashStr());
            if (hashset != null && hashset.size() > 0) {
                xhtml.newline();
                xhtml.characters(hashset.toString());
            }
            xhtml.endElement("td"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

            attributes = new AttributesImpl();
            attributes.addAttribute("", "class", "class", "CDATA", "sizetd");
            xhtml.startElement("td", attributes); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            xhtml.characters(Long.toString(cf.getSize()));
            totalSize+=cf.getSize();
            xhtml.endElement("td"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$

            xhtml.endElement("tr"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            i++;
        }
        if (linkedItens != "") {
            linkedItens = linkedItens.substring(3);
            metadata.add(ExtraProperties.LINKED_ITEMS, linkedItens);
        }
        xhtml.endElement("table"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        xhtml.startElement("p");
        xhtml.characters(Messages.getString("EmuleCollectionParser.CollectionTotalSize") + totalSize);
        xhtml.endElement("p");
        xhtml.endDocument();
    }
}
