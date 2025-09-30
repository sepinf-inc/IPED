package iped.parsers.emule;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import org.apache.commons.io.IOUtils;
import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
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

import iped.data.IItemReader;
import iped.parsers.util.BeanMetadataExtraction;
import iped.parsers.util.ChildPornHashLookup;
import iped.parsers.util.Messages;
import iped.parsers.util.P2PUtil;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.search.IItemSearcher;
import iped.utils.LocalizedFormat;

/**
 * e-Mule "part.met" files parser. These files store information about files
 * being downloaded, which in some case are not present in the main e-Mule
 * control file (known.met).
 * 
 * @author Wladimir
 */
public class PartMetParser extends AbstractParser {
    private static final long serialVersionUID = 6100522577461358577L;

    public static final String EMULE_PART_MET_MIME_TYPE = "application/x-emule-part-met";
    public static final String PART_MET_ENTRY_MIME_TYPE = "application/x-emule-part-met-entry";

    private static final Set<MediaType> SUPPORTED_TYPES = Collections
            .singleton(MediaType.parse(EMULE_PART_MET_MIME_TYPE));

    private boolean extractEntries = false;

    @Field
    public void setExtractEntries(boolean value) {
        this.extractEntries = value;
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        final DecimalFormat nf = LocalizedFormat.getDecimalInstance("#,##0");
        final DateFormat df = new SimpleDateFormat(Messages.getString("KnownMetParser.DataFormat"));
        df.setTimeZone(TimeZone.getTimeZone("GMT+0"));

        metadata.set(HttpHeaders.CONTENT_TYPE, EMULE_PART_MET_MIME_TYPE);
        metadata.remove(TikaCoreProperties.RESOURCE_NAME_KEY);

        // PART.MET files are very small, so 1 MB should be more than enough.
        byte[] bytes = new byte[1 << 20];
        IOUtils.read(stream, bytes);
        int version = bytes[0] & 0xFF;
        if (version != 224 && version != 225 && version != 226) {
            throw new TikaException("Detected part.met file format version not supported: " + version);
        }

        KnownMetEntry e = new KnownMetEntry();
        int ret = iped.parsers.emule.KnownMetDecoder.parseEntry(e, 1, bytes, false);
        if (ret <= 0) {
            throw new TikaException("part.met file parsing returned error code " + ret);
        }

        metadata.add(ExtraProperties.SHARED_HASHES, e.getHash());
        metadata.set(ExtraProperties.P2P_REGISTRY_COUNT, String.valueOf(1));
        
        IItemSearcher searcher = context.get(IItemSearcher.class);

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        xhtml.startElement("style");
        xhtml.characters(
                ".d { border-collapse: collapse; font-family: Arial, sans-serif; margin-right: 32px; margin-bottom: 32px; text-align: left; vertical-align: middle; } "
                        + ".a { border: solid; border-width: thin; font-weight: bold; background-color:#CCCCEE; padding: 4px; } "
                        + ".b { border: solid; border-width: thin; padding: 4px; } "
                        + ".c { border: solid; border-width: thin; padding: 4px; font-family: monospace; font-size: larger; } ");
        xhtml.endElement("style");
        xhtml.newline();

        xhtml.startElement("table", "class", "d");

        int hashDBHits = 0;
        List<String> hashSets = ChildPornHashLookup.lookupHash(KnownMetParser.EDONKEY, e.getHash());
        IItemReader item = P2PUtil.searchItemInCase(searcher, KnownMetParser.EDONKEY, e.getHash());
        if (item != null) {
            hashSets = ChildPornHashLookup.lookupHashAndMerge(item.getHash(), hashSets);
            e.setFoundInCase(true);
        }
        if (hashSets != null && !hashSets.isEmpty()) {
            hashDBHits++;
            e.setFoundInHashDB(hashSets.toString());
        }

        if (extractEntries) {
            BeanMetadataExtraction bme = new BeanMetadataExtraction(ExtraProperties.P2P_META_PREFIX, PART_MET_ENTRY_MIME_TYPE, context);
            // normalization to use same property name of other p2p parsers
            bme.registerPropertyNameMapping(KnownMetEntry.class, "hash", "ed2k");
            bme.registerTransformationMapping(KnownMetEntry.class, ExtraProperties.LINKED_ITEMS, "edonkey:${hash}");
            bme.registerTransformationMapping(KnownMetEntry.class, ExtraProperties.SHARED_HASHES, "${sent != null && sent ? hash : null}");
            bme.registerTransformationMapping(KnownMetEntry.class, BasicProps.NAME, "Part-Entry-[${name}].met");
            bme.extractEmbedded(0, context, metadata, handler, e);
        }

        AttributesImpl attributes = new AttributesImpl();
        if (e.getHash() != null && !e.getHash().isEmpty())
            attributes.addAttribute("", "name", "name", "CDATA", e.getHash().toUpperCase());    
        xhtml.startElement("tr", attributes);
        xhtml.startElement("td", "class", "a");
        xhtml.characters(Messages.getString("KnownMetParser.Name"));
        xhtml.endElement("td");
        xhtml.startElement("td", "class", "b");
        if (item != null)
            P2PUtil.printNameWithLink(xhtml, item, e.getName());
        else
            xhtml.characters(e.getName());
        xhtml.endElement("td");
        xhtml.endElement("tr");
        xhtml.newline();

        addLine(xhtml, Messages.getString("KnownMetParser.Hash"), e.getHash(), "c");
        addLine(xhtml, Messages.getString("KnownMetParser.LastModDate"), toFormatStr(df, e.getLastModified()));
        addLine(xhtml, Messages.getString("KnownMetParser.LastPubDate"), toFormatStr(df, e.getLastPublishedKad()));
        addLine(xhtml, Messages.getString("KnownMetParser.LastShareDate").replaceAll("-", ""), toFormatStr(df, e.getLastShared()));
        addLine(xhtml, Messages.getString("KnownMetParser.Size"), toFormatStr(nf, e.getFileSize()));
        addLine(xhtml, Messages.getString("KnownMetParser.Requests"), toFormatStr(nf, e.getTotalRequests()));
        addLine(xhtml, Messages.getString("KnownMetParser.AcceptedRequests"), toFormatStr(nf, e.getAcceptedRequests()));
        addLine(xhtml, Messages.getString("KnownMetParser.BytesSent"), toFormatStr(nf, e.getBytesTransfered()));
        addLine(xhtml, Messages.getString("KnownMetParser.TempFile"), nvlStr(e.getPartName()));
        addLine(xhtml, Messages.getString("KnownMetParser.FoundInPedoHashDB"),
                hashSets.isEmpty() ? "" : hashSets.toString());
        addLine(xhtml, Messages.getString("KnownMetParser.FoundInCase"),
                item != null ? Messages.getString("KnownMetParser.Yes") : "");

        if (hashDBHits > 0)
            metadata.set(ExtraProperties.CSAM_HASH_HITS, Integer.toString(hashDBHits));
        
        xhtml.endElement("table");
        xhtml.endDocument();
    }

    private void addLine(XHTMLContentHandler xhtml, String key, String value, String valueClass) throws SAXException {
        xhtml.startElement("tr");
        xhtml.startElement("td", "class", "a");
        xhtml.characters(key);
        xhtml.endElement("td");
        xhtml.startElement("td", "class", valueClass);
        xhtml.characters(value);
        xhtml.endElement("td");
        xhtml.endElement("tr");
        xhtml.newline();
    }

    private void addLine(XHTMLContentHandler xhtml, String key, String value) throws SAXException {
        addLine(xhtml, key, value, "b");
    }

    private String nvlStr(String str) {
        return str == null ? "-" : str;
    }

    private String toFormatStr(DateFormat df, Date date) {
        return date == null ? "-" : df.format(date);
    }

    private String toFormatStr(NumberFormat nf, long v) {
        return v == -1 ? "-" : nf.format(v);
    }
}