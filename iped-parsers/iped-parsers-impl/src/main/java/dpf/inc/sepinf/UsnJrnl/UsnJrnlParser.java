package dpf.inc.sepinf.UsnJrnl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Set;

import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.util.EmptyInputStream;

public class UsnJrnlParser extends AbstractParser {
    public enum ReportType {
        CSV, HTML
    };

    ReportType reportType = ReportType.CSV;

    private static final int MAX_ENTRIES = 10000;

    // Option to extract each registry as a sub item.
    private boolean extractEntries = false;

    public static final MediaType USNJRNL_$J = MediaType.parse("USNJOURNAL/$J");
    public static final MediaType USNJRNL_REPORT_HTM = MediaType.parse("USNJOURNAL/Report-HTML");
    public static final MediaType USNJRNL_REPORT_CSV = MediaType.parse("USNJOURNAL/Report-CSV");
    public static final MediaType USNJRNL_REGISTRY = MediaType.parse("USNJOURNAL/registry");

    private static Set<MediaType> SUPPORTED_TYPES = MediaType.set(USNJRNL_$J);

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Field
    public void setExtractEntries(boolean extractEntries) {
        this.extractEntries = extractEntries;
    }



    public boolean findNextEntry(InputStream in) throws IOException {
        byte[] b = new byte[8];
        int rb = -1;
        do {
            in.mark(8);
            rb = in.read(b, 0, 8);

            if (Util.zero(b)) {
                continue;
            }
            in.reset();

            if (b[4] == 2 && b[5] + b[6] + b[7] == 0) {
                return true;
            }
            in.read();

        } while (rb == 8);

        return false;
    }

    public UsnJrnlEntry readEntry(InputStream in) throws IOException {
        in.mark(4);
        int tam = Util.readInt32(in);
        in.reset();
        in.mark(tam);
        in.skip(4);
        if (tam > 0) {
            UsnJrnlEntry u = new UsnJrnlEntry();
            u.setTam(tam);
            u.setMajorVersion(Util.readInt16(in));
            u.setMinorVersion(Util.readInt16(in));
            u.setMftRef(Util.readInt64(in));
            u.setParentMftRef(Util.readInt64(in));
            u.setUSN(Util.readInt64(in));
            long filetime = Util.readInt64(in);
            u.setFileTime(filetime);
            u.setReasonFlag(Util.readInt32(in));
            u.setSourceInformation(Util.readInt32(in));
            u.setSecurityId(Util.readInt32(in));
            u.setFileAttributes(Util.readInt32(in));
            u.setSizeofFileName(Util.readInt16(in));
            u.setOffsetFilename(Util.readInt16(in));
            if (u.getOffsetFilename() != 0x3c) {
                System.out.println("error");
                return null;
            } else {
                u.setFileName(Util.readString(in, u.getSizeofFileName()));

            }
            in.reset();
            while (tam > 0) {
                tam -= in.skip(tam);
            }
            return u;
        }

        return null;
    }

    private void createReport(ArrayList<UsnJrnlEntry> entries, int n, ParseContext context, ContentHandler handler)
            throws SAXException, IOException {
        ReportGenerator rg = new ReportGenerator();
        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class,
                new ParsingEmbeddedDocumentExtractor(context));
        byte[] bytes = null;
        

        Metadata cMetadata = new Metadata();
        if (reportType == ReportType.CSV) {
            cMetadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, USNJRNL_REPORT_CSV.toString());
            bytes = rg.createCSVReport(entries);
        }

        if (reportType == ReportType.HTML) {
            cMetadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, USNJRNL_REPORT_HTM.toString());
            bytes=rg.createHTMLReport(entries);
        }
        
        ByteArrayInputStream html = new ByteArrayInputStream(bytes);

        cMetadata.set(TikaCoreProperties.TITLE, "JOURNAL " + n);

        extractor.parseEmbedded(html, handler, cMetadata, false);

        /**
         * Optionally extract entries as subitems
         */
        if (extractEntries) {
            for(UsnJrnlEntry entry:entries) {
                extractor = context.get(EmbeddedDocumentExtractor.class, new ParsingEmbeddedDocumentExtractor(context));
                Metadata metadataItem = new Metadata();
                metadataItem.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, USNJRNL_REGISTRY.toString());
                metadataItem.set(TikaCoreProperties.TITLE, "USN journal Entry " + entry.getUSN());
    

                metadataItem.set(TikaCoreProperties.CREATED, ReportGenerator.timeFormat.format(entry.getFileTime()));
                metadataItem.set("FileName", entry.getFileName());
                metadataItem.set("USN", entry.getUSN() + "");
                metadataItem.set("MTF Ref", entry.getMftRef() + "");
                metadataItem.set("Parent MTF Ref", entry.getParentMftRef() + "");
                metadataItem.set("Reasons", entry.getReasons());
                metadataItem.set("File Attributes", entry.getHumanAttributes());
                extractor.parseEmbedded(new EmptyInputStream(), handler, metadataItem, false);

            }
        }
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        ArrayList<UsnJrnlEntry> entries = new ArrayList<>();
        int n = 1;
        while (findNextEntry(stream)) {
            UsnJrnlEntry u = readEntry(stream);

            if (u == null) {
                continue;
            }

            // limits the html table size
            if (entries.size() == MAX_ENTRIES && reportType == ReportType.HTML) {
                createReport(entries, n, context, handler);
                entries.clear();
                n++;
            }

            entries.add(u);
        }

        if (entries.size() > 0) {
            createReport(entries, n, context, handler);
        }

    }

}
