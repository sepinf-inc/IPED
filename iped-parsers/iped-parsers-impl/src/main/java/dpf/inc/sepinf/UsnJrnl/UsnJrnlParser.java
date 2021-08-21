package dpf.inc.sepinf.UsnJrnl;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;
import dpf.sp.gpinf.indexer.util.EmptyInputStream;
import dpf.sp.gpinf.indexer.util.IOUtil;
import iped3.io.IItemBase;
import iped3.io.SeekableInputStream;
import iped3.search.IItemSearcher;
import iped3.util.BasicProps;

public class UsnJrnlParser extends AbstractParser {
    public enum ReportType {
        CSV, HTML
    };

    // select if the report will be in CSV or HTML format
    private ReportType reportType = ReportType.CSV;

    // max entries for html report or parent search
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

    public boolean findNextEntry(SeekableInputStream in) throws IOException {
        byte[] b = new byte[8];
        int rb = 0;
        do {
            long pos = in.position();
            rb = IOUtils.read(in, b, 0, 8);

            // if all zeros read next 8 bytes
            if (Util.zero(b)) {
                continue;
            }

            in.seek(pos);
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

        Metadata cMetadata = new Metadata();
        String name = "USN Journal Report";

        InputStream is = null;
        try (TemporaryResources tmp = new TemporaryResources()) {
            if (reportType == ReportType.CSV) {
                cMetadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, USNJRNL_REPORT_CSV.toString());
                is = rg.createCSVReport(entries, tmp);

            } else if (reportType == ReportType.HTML) {
                cMetadata.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, USNJRNL_REPORT_HTML.toString());
                is = rg.createHTMLReport(entries);
                name += " " + n;
            }

            cMetadata.set(TikaCoreProperties.TITLE, name);
            extractor.parseEmbedded(is, handler, cMetadata, false);

        } finally {
            IOUtil.closeQuietly(is);
        }

        /**
         * Optionally extract entries as subitems
         */
        if (extractEntries) {
            for (UsnJrnlEntry entry : entries) {
                extractor = context.get(EmbeddedDocumentExtractor.class, new ParsingEmbeddedDocumentExtractor(context));
                Metadata metadataItem = new Metadata();
                metadataItem.set(IndexerDefaultParser.INDEXER_CONTENT_TYPE, USNJRNL_REGISTRY.toString());
                metadataItem.set(TikaCoreProperties.TITLE, "USN journal Entry " + entry.getUSN());
                metadataItem.set(BasicProps.LENGTH, "");

                String[] props = ReportGenerator.cols;

                metadataItem.set(TikaCoreProperties.CREATED, rg.timeFormat.format(entry.getFileTime()));
                metadataItem.set(ReportGenerator.cols[0], String.format("0x%016X", entry.getOffset()));
                metadataItem.set(props[1], entry.getFileName());
                metadataItem.set(props[2], entry.getFullPath());
                metadataItem.set(props[3], Long.toString(entry.getUSN()));
                for (String value : entry.getReasons()) {
                    metadataItem.add(props[5], value);
                }
                metadataItem.set(props[6], "0x" + Util.byteArrayToHex(entry.getMftRef()));
                metadataItem.set(props[7], "0x" + Util.byteArrayToHex(entry.getParentMftRef()));
                for (String value : entry.getHumanAttributes()) {
                    metadataItem.add(props[8], value);
                }
                metadataItem.set(props[9], Long.toString(entry.getSourceInformation()));
                metadataItem.set(props[10], Long.toString(entry.getSecurityId()));
                extractor.parseEmbedded(new EmptyInputStream(), handler, metadataItem, false);

            }
        }
    }

    private static final int READ_PAGE = 0XFFFF;

    public long jumpZeros(SeekableInputStream in, long start, long end) throws IOException {

        long pos = (start + end) / 2;
        in.seek(pos);

        byte buff[] = new byte[READ_PAGE];
        int rb = IOUtils.read(in, buff, 0, READ_PAGE);
        if (Util.zero(buff) && rb == READ_PAGE) {
            return jumpZeros(in, pos, end);
        } else {
            in.seek(start);
            do {
                rb = IOUtils.read(in, buff, 0, READ_PAGE);
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
        IItemSearcher searcher = context.get(IItemSearcher.class);
        IItemBase item = context.get(IItemBase.class);
        try (SeekableInputStream sis = item.getStream()) {
            jumpZeros(sis, 0, sis.size());
            while (findNextEntry(sis)) {
                UsnJrnlEntry u = readEntry(sis);
                // do not insert empty registries in the list
                if (u == null) {
                    continue;
                }
                
                entries.add(u);

                if (entries.size() % MAX_ENTRIES == 0) {
                    int baseIndex = ((entries.size() / MAX_ENTRIES) - 1) * MAX_ENTRIES;
                    rebuildFullPaths(entries.subList(baseIndex, baseIndex + MAX_ENTRIES), searcher, item);
                }

                // limits the html table size
                if (entries.size() == MAX_ENTRIES && reportType == ReportType.HTML) {
                    createReport(entries, n, context, handler);
                    entries.clear();
                    n++;
                }
            }
        }

        if (entries.size() > 0) {
            if (entries.size() % MAX_ENTRIES != 0) {
                int baseIndex = (entries.size() / MAX_ENTRIES) * MAX_ENTRIES;
                rebuildFullPaths(entries.subList(baseIndex, entries.size()), searcher, item);
            }
            createReport(entries, n, context, handler);
        }

    }

    private void rebuildFullPaths(List<UsnJrnlEntry> entries, IItemSearcher searcher, IItemBase item) {
        StringBuilder query = new StringBuilder();
        query.append(BasicProps.EVIDENCE_UUID + ":" + item.getDataSource().getUUID());
        query.append(" && ");
        query.append(BasicProps.FILESYSTEM_ID + ":" + item.getExtraAttribute(BasicProps.FILESYSTEM_ID));
        query.append(" && (");
        HashSet<Long> parentRefs = new HashSet<>();
        for (UsnJrnlEntry entry : entries) {
            parentRefs.add(entry.getParentMftRefAsLong());
        }
        for (Long parentRef : parentRefs) {
            long metaAddr = parentRef.longValue() & 0xFFFFFFFFFFFFl;
            long metaSeq = parentRef.longValue() >> 48;
            if (metaAddr >= 0 && metaSeq >= 0) {
                query.append("(");
                query.append(BasicProps.META_ADDRESS + ":" + metaAddr);
                query.append(" && ");
                query.append(BasicProps.MFT_SEQUENCE + ":" + metaSeq);
                query.append(") ");
            }
        }
        query.append(")");
        List<IItemBase> parents = searcher.search(query.toString());
        HashMap<Long, IItemBase> map = new HashMap<>();
        for (IItemBase parent : parents) {
            long meta = Long.parseLong((String) parent.getExtraAttribute(BasicProps.META_ADDRESS));
            long seq = Long.parseLong((String) parent.getExtraAttribute(BasicProps.MFT_SEQUENCE));
            long fullRef = seq << 48 | meta;
            map.put(fullRef, parent);
        }
        for (UsnJrnlEntry entry : entries) {
            IItemBase parent = map.get(entry.getParentMftRefAsLong());
            if (parent != null) {
                entry.setFullPath(parent.getPath() + "/" + entry.getFileName());
            }
        }

    }

}
