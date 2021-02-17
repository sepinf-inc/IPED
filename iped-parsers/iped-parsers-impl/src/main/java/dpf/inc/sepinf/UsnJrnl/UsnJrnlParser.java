package dpf.inc.sepinf.UsnJrnl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Set;

import org.apache.commons.io.IOUtils;
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
import iped3.io.IItemBase;
import iped3.io.SeekableInputStream;

public class UsnJrnlParser extends AbstractParser {
    public enum ReportType {
        CSV, HTML
    };

    // select if the report will be in CSV or HTML format
    private ReportType reportType = ReportType.CSV;

    // max entries for html report
    private static final int MAX_ENTRIES = 10000;

    // Option to extract each registry as a sub item.
    private boolean extractEntries = false;

    public static final MediaType USNJRNL_$J = MediaType.parse("application/x-usnjournal-$J");
    public static final MediaType USNJRNL_REPORT_HTML = MediaType.parse("application/x-usnjournal-report-html");
    public static final MediaType USNJRNL_REPORT_CSV = MediaType.parse("application/x-usnjournal-report-csv");
    public static final MediaType USNJRNL_REGISTRY = MediaType.parse("application/x-usnjournal-registry");

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
        int rb = 0;
        do {
            in.mark(8);
            rb = IOUtils.read(in, b, 0, 8);

            // if all zeros read next 8 bytes
            if (Util.zero(b)) {
                continue;
            }

            in.reset();
            // usn entry version 2.0
            if (b[4] == 2 && (b[5] | b[6] | b[7]) == 0) {
                return true;
            }
            // usn entry version 3.0
            if (b[4] == 3 && (b[5] | b[6] | b[7]) == 0) {
                return true;
            }
            // advances one byte
            in.read();

        } while (rb > 0);

        return false;
    }

    public UsnJrnlEntry readEntry(SeekableInputStream in) throws IOException {
        long pos = in.position();
        int tam = (int) Util.readInt32(in);

        if (tam > 0) {
            UsnJrnlEntry u = new UsnJrnlEntry();
            u.setTam(tam);
            u.setOffset(pos);
            u.setMajorVersion(Util.readInt16(in));
            u.setMinorVersion(Util.readInt16(in));
            int filerefLen = 8;
            if (u.getMajorVersion() == 3) {
                filerefLen = 16;
            }
            u.setMftRef(IOUtils.readFully(in, filerefLen));
            u.setParentMftRef(IOUtils.readFully(in, filerefLen));
            u.setUSN(Util.readInt64(in));
            long filetime = Util.readInt64(in);
            u.setFileTime(filetime);
            u.setReasonFlag(Util.readInt32(in));
            u.setSourceInformation(Util.readInt32(in));
            u.setSecurityId(Util.readInt32(in));
            u.setFileAttributes(Util.readInt32(in));
            u.setSizeofFileName(Util.readInt16(in));
            u.setOffsetFilename(Util.readInt16(in));
            // invalid registry
            if (u.getOffsetFilename() + u.getSizeofFileName() > tam) {
                return null;
            } else {
                u.setFileName(Util.readString(in, u.getSizeofFileName()));
            }
            if (in.position() < pos + tam) {
                in.seek(pos + tam);
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
            cMetadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, USNJRNL_REPORT_HTML.toString());
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
    

                metadataItem.set(TikaCoreProperties.CREATED, rg.timeFormat.format(entry.getFileTime()));
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

    private static final int READ_PAGE = 0XFFFF;

    public long jumpZeros(SeekableInputStream in, long start, long end) throws IOException {

        long pos=(start + end) / 2;
        in.seek(pos);

        byte buff[] = new byte[READ_PAGE];
        int rb = in.read(buff, 0, READ_PAGE);
       if(Util.zero(buff) && rb == READ_PAGE){
           return jumpZeros(in, pos, end);
       }else {

           in.seek(start);
           do {
               rb = in.read(buff, 0, READ_PAGE);
           } while (Util.zero(buff) && rb == READ_PAGE);
           pos = in.position() - rb;
           in.seek(pos);
           return pos;
       }

       
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        ArrayList<UsnJrnlEntry> entries = new ArrayList<>();
        int n = 1;
        IItemBase item = context.get(IItemBase.class);
        SeekableInputStream sis = item.getStream();
        jumpZeros(sis, 0, sis.size());
        while (findNextEntry(sis)) {
            UsnJrnlEntry u = readEntry(sis);
            // do not insert empty registries in the list
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
