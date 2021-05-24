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

        String a = "evidenceUUID:d78c007d-c88a-4660-96cd-15dc3e170dae && fileSystemId:null && ((metaAddress:190495 && MFTSequence:29) (metaAddress:190495 && MFTSequence:28) (metaAddress:143369 && MFTSequence:9) (metaAddress:190495 && MFTSequence:30) (metaAddress:75782 && MFTSequence:3) (metaAddress:190495 && MFTSequence:27) (metaAddress:77320 && MFTSequence:2) (metaAddress:207916 && MFTSequence:36) (metaAddress:118839 && MFTSequence:61) (metaAddress:141380 && MFTSequence:74) (metaAddress:104582 && MFTSequence:139) (metaAddress:113718 && MFTSequence:57) (metaAddress:78519 && MFTSequence:185) (metaAddress:75803 && MFTSequence:8) (metaAddress:104582 && MFTSequence:148) (metaAddress:51222 && MFTSequence:1) (metaAddress:51429 && MFTSequence:-14094) (metaAddress:216602 && MFTSequence:1) (metaAddress:207915 && MFTSequence:48) (metaAddress:51289 && MFTSequence:64) (metaAddress:323125 && MFTSequence:43) (metaAddress:141374 && MFTSequence:30) (metaAddress:44073 && MFTSequence:5) (metaAddress:161325 && MFTSequence:1) (metaAddress:169994 && MFTSequence:56) (metaAddress:141366 && MFTSequence:13) (metaAddress:56 && MFTSequence:5) (metaAddress:212049 && MFTSequence:18) (metaAddress:207440 && MFTSequence:18) (metaAddress:207440 && MFTSequence:17) (metaAddress:161351 && MFTSequence:6) (metaAddress:54856 && MFTSequence:14) (metaAddress:169963 && MFTSequence:430) (metaAddress:141382 && MFTSequence:16) (metaAddress:209477 && MFTSequence:19) (metaAddress:201832 && MFTSequence:48) (metaAddress:199276 && MFTSequence:9) (metaAddress:110204 && MFTSequence:26) (metaAddress:207478 && MFTSequence:25) (metaAddress:207476 && MFTSequence:26) (metaAddress:126044 && MFTSequence:51) (metaAddress:151630 && MFTSequence:35) (metaAddress:207478 && MFTSequence:26) (metaAddress:146969 && MFTSequence:619) (metaAddress:334458 && MFTSequence:6) (metaAddress:207972 && MFTSequence:30) (metaAddress:207476 && MFTSequence:13) (metaAddress:207972 && MFTSequence:29) (metaAddress:353912 && MFTSequence:6) (metaAddress:194653 && MFTSequence:33) (metaAddress:3203 && MFTSequence:1) (metaAddress:3202 && MFTSequence:1) (metaAddress:153802 && MFTSequence:76) (metaAddress:353922 && MFTSequence:2) (metaAddress:90243 && MFTSequence:5) (metaAddress:64735 && MFTSequence:83) (metaAddress:336015 && MFTSequence:4) (metaAddress:335503 && MFTSequence:5) (metaAddress:109200 && MFTSequence:1) (metaAddress:18578 && MFTSequence:3) (metaAddress:87701 && MFTSequence:6) (metaAddress:87699 && MFTSequence:6) (metaAddress:87708 && MFTSequence:6) (metaAddress:207489 && MFTSequence:39) (metaAddress:212128 && MFTSequence:11) (metaAddress:207489 && MFTSequence:43) (metaAddress:110212 && MFTSequence:42) (metaAddress:212128 && MFTSequence:12) (metaAddress:207489 && MFTSequence:45) (metaAddress:64181 && MFTSequence:6) (metaAddress:335541 && MFTSequence:9) (metaAddress:78010 && MFTSequence:1) (metaAddress:115386 && MFTSequence:1) (metaAddress:78011 && MFTSequence:1) (metaAddress:207529 && MFTSequence:22) (metaAddress:207529 && MFTSequence:23) (metaAddress:76989 && MFTSequence:3) (metaAddress:207529 && MFTSequence:21) (metaAddress:99015 && MFTSequence:5) (metaAddress:87744 && MFTSequence:9) (metaAddress:78025 && MFTSequence:1) (metaAddress:78026 && MFTSequence:1) (metaAddress:213229 && MFTSequence:32) (metaAddress:213235 && MFTSequence:35) (metaAddress:213210 && MFTSequence:10) (metaAddress:213210 && MFTSequence:13) (metaAddress:213235 && MFTSequence:38) (metaAddress:213235 && MFTSequence:40) (metaAddress:213235 && MFTSequence:41) (metaAddress:213230 && MFTSequence:13) (metaAddress:213226 && MFTSequence:9) (metaAddress:213231 && MFTSequence:12) (metaAddress:213234 && MFTSequence:16) (metaAddress:213231 && MFTSequence:13) (metaAddress:213230 && MFTSequence:15) (metaAddress:213231 && MFTSequence:14) (metaAddress:213230 && MFTSequence:14) (metaAddress:213226 && MFTSequence:10) (metaAddress:213232 && MFTSequence:16) (metaAddress:213228 && MFTSequence:11) (metaAddress:213229 && MFTSequence:11) (metaAddress:213226 && MFTSequence:12) (metaAddress:75502 && MFTSequence:9) (metaAddress:213231 && MFTSequence:11) (metaAddress:76011 && MFTSequence:3) (metaAddress:213223 && MFTSequence:8) (metaAddress:213229 && MFTSequence:30) (metaAddress:213240 && MFTSequence:11) (metaAddress:199878 && MFTSequence:52) (metaAddress:213240 && MFTSequence:10) (metaAddress:213241 && MFTSequence:11) (metaAddress:213239 && MFTSequence:6) (metaAddress:213241 && MFTSequence:8) (metaAddress:213240 && MFTSequence:9) (metaAddress:213242 && MFTSequence:11) (metaAddress:213239 && MFTSequence:7) (metaAddress:213229 && MFTSequence:29) (metaAddress:213240 && MFTSequence:8) (metaAddress:213242 && MFTSequence:10) (metaAddress:213243 && MFTSequence:11) (metaAddress:213245 && MFTSequence:10) (metaAddress:213246 && MFTSequence:9) (metaAddress:213233 && MFTSequence:7) (metaAddress:213225 && MFTSequence:28) (metaAddress:213240 && MFTSequence:13) (metaAddress:213245 && MFTSequence:9) (metaAddress:213246 && MFTSequence:10) (metaAddress:213247 && MFTSequence:11) (metaAddress:153812 && MFTSequence:46) (metaAddress:213227 && MFTSequence:16) (metaAddress:213245 && MFTSequence:6) (metaAddress:207938 && MFTSequence:185) (metaAddress:213244 && MFTSequence:7) (metaAddress:213232 && MFTSequence:10) (metaAddress:213245 && MFTSequence:7) (metaAddress:207938 && MFTSequence:184) (metaAddress:213238 && MFTSequence:12) (metaAddress:213234 && MFTSequence:11) (metaAddress:213227 && MFTSequence:18) (metaAddress:213234 && MFTSequence:10) (metaAddress:213245 && MFTSequence:5) (metaAddress:213246 && MFTSequence:6) (metaAddress:213233 && MFTSequence:9) (metaAddress:213238 && MFTSequence:14) (metaAddress:213237 && MFTSequence:13) (metaAddress:213236 && MFTSequence:12) (metaAddress:213238 && MFTSequence:9) (metaAddress:213242 && MFTSequence:5) (metaAddress:213234 && MFTSequence:13) (metaAddress:213227 && MFTSequence:20) (metaAddress:213237 && MFTSequence:10) (metaAddress:213236 && MFTSequence:11) (metaAddress:213240 && MFTSequence:6) (metaAddress:213232 && MFTSequence:14) (metaAddress:213237 && MFTSequence:11) (metaAddress:213243 && MFTSequence:6) (metaAddress:213237 && MFTSequence:8) (metaAddress:213234 && MFTSequence:15) (metaAddress:213242 && MFTSequence:6) (metaAddress:213234 && MFTSequence:14) (metaAddress:213239 && MFTSequence:11) (metaAddress:105216 && MFTSequence:1) (metaAddress:105217 && MFTSequence:1) (metaAddress:213252 && MFTSequence:6) (metaAddress:213252 && MFTSequence:5) (metaAddress:213252 && MFTSequence:4) (metaAddress:213255 && MFTSequence:7) (metaAddress:91396 && MFTSequence:3) (metaAddress:213249 && MFTSequence:4) (metaAddress:213250 && MFTSequence:7) (metaAddress:213249 && MFTSequence:5) (metaAddress:359180 && MFTSequence:1) (metaAddress:213261 && MFTSequence:6) (metaAddress:213262 && MFTSequence:5) (metaAddress:213260 && MFTSequence:5) (metaAddress:213253 && MFTSequence:10) (metaAddress:213291 && MFTSequence:36) (metaAddress:213259 && MFTSequence:6) (metaAddress:213256 && MFTSequence:5) (metaAddress:213253 && MFTSequence:9) (metaAddress:213278 && MFTSequence:13) (metaAddress:213274 && MFTSequence:8) (metaAddress:213270 && MFTSequence:7) (metaAddress:213273 && MFTSequence:9) (metaAddress:213265 && MFTSequence:6) (metaAddress:213266 && MFTSequence:5) (metaAddress:213279 && MFTSequence:8) (metaAddress:213263 && MFTSequence:20) (metaAddress:213277 && MFTSequence:6) (metaAddress:213271 && MFTSequence:9) (metaAddress:209197 && MFTSequence:15) (metaAddress:101716 && MFTSequence:112) (metaAddress:213280 && MFTSequence:6) (metaAddress:213301 && MFTSequence:30) (metaAddress:213306 && MFTSequence:16) (metaAddress:213282 && MFTSequence:8) (metaAddress:213301 && MFTSequence:28) (metaAddress:213281 && MFTSequence:8) (metaAddress:210744 && MFTSequence:17) (metaAddress:213286 && MFTSequence:9) (metaAddress:213296 && MFTSequence:31) (metaAddress:213307 && MFTSequence:21) (metaAddress:213296 && MFTSequence:29) (metaAddress:213307 && MFTSequence:22) (metaAddress:213285 && MFTSequence:9) (metaAddress:213311 && MFTSequence:12) (metaAddress:213309 && MFTSequence:12) (metaAddress:173848 && MFTSequence:41) (metaAddress:213305 && MFTSequence:14) (metaAddress:210744 && MFTSequence:15) (metaAddress:213309 && MFTSequence:11) (metaAddress:213311 && MFTSequence:10) (metaAddress:213310 && MFTSequence:11) (metaAddress:213310 && MFTSequence:10) (metaAddress:13348 && MFTSequence:798) (metaAddress:207655 && MFTSequence:30) (metaAddress:13348 && MFTSequence:799) (metaAddress:213299 && MFTSequence:12) (metaAddress:213302 && MFTSequence:9) (metaAddress:207183 && MFTSequence:12) (metaAddress:212836 && MFTSequence:37) (metaAddress:75759 && MFTSequence:1197) (metaAddress:213322 && MFTSequence:10) (metaAddress:21017 && MFTSequence:346) (metaAddress:2885 && MFTSequence:1) (metaAddress:213320 && MFTSequence:15) (metaAddress:213325 && MFTSequence:10) (metaAddress:213327 && MFTSequence:8) (metaAddress:213320 && MFTSequence:14) (metaAddress:202578 && MFTSequence:22) (metaAddress:213324 && MFTSequence:8) (metaAddress:208740 && MFTSequence:32) (metaAddress:213314 && MFTSequence:9) (metaAddress:213324 && MFTSequence:7) (metaAddress:213312 && MFTSequence:10) (metaAddress:331 && MFTSequence:1) (metaAddress:213314 && MFTSequence:10) (metaAddress:333 && MFTSequence:1) (metaAddress:332 && MFTSequence:1) (metaAddress:213319 && MFTSequence:11) (metaAddress:213323 && MFTSequence:7) (metaAddress:64058 && MFTSequence:362) (metaAddress:64058 && MFTSequence:360) (metaAddress:64058 && MFTSequence:361) (metaAddress:213317 && MFTSequence:18) (metaAddress:13603 && MFTSequence:116) (metaAddress:213317 && MFTSequence:17) (metaAddress:335705 && MFTSequence:4) (metaAddress:212840 && MFTSequence:51) (metaAddress:212840 && MFTSequence:50) (metaAddress:357215 && MFTSequence:1) (metaAddress:213321 && MFTSequence:22) (metaAddress:213321 && MFTSequence:23) (metaAddress:212846 && MFTSequence:12) (metaAddress:212847 && MFTSequence:13) (metaAddress:13152 && MFTSequence:2) (metaAddress:212843 && MFTSequence:12) (metaAddress:212844 && MFTSequence:10) (metaAddress:212843 && MFTSequence:13) (metaAddress:212848 && MFTSequence:21) (metaAddress:357217 && MFTSequence:3) (metaAddress:357219 && MFTSequence:1) (metaAddress:212847 && MFTSequence:11) (metaAddress:212839 && MFTSequence:12) (metaAddress:212835 && MFTSequence:12) (metaAddress:357225 && MFTSequence:1) (metaAddress:213300 && MFTSequence:90) (metaAddress:212835 && MFTSequence:13) (metaAddress:213344 && MFTSequence:12) (metaAddress:75632 && MFTSequence:1) (metaAddress:75634 && MFTSequence:1) (metaAddress:19325 && MFTSequence:5) (metaAddress:208740 && MFTSequence:31) (metaAddress:210287 && MFTSequence:21) (metaAddress:197446 && MFTSequence:63) (metaAddress:212842 && MFTSequence:19) (metaAddress:210287 && MFTSequence:23) (metaAddress:212842 && MFTSequence:18) (metaAddress:189286 && MFTSequence:24) (metaAddress:210259 && MFTSequence:44) (metaAddress:212841 && MFTSequence:23) (metaAddress:189286 && MFTSequence:25) (metaAddress:51069 && MFTSequence:3) (metaAddress:189286 && MFTSequence:26) (metaAddress:189286 && MFTSequence:27) (metaAddress:13189 && MFTSequence:2) (metaAddress:104896 && MFTSequence:73) (metaAddress:76170 && MFTSequence:2) (metaAddress:18235 && MFTSequence:181) (metaAddress:178579 && MFTSequence:30) (metaAddress:173959 && MFTSequence:36) (metaAddress:139183 && MFTSequence:14) (metaAddress:178565 && MFTSequence:35) (metaAddress:114623 && MFTSequence:27) (metaAddress:91404 && MFTSequence:170) (metaAddress:104344 && MFTSequence:49) (metaAddress:347055 && MFTSequence:2) (metaAddress:104344 && MFTSequence:48) (metaAddress:210874 && MFTSequence:16) (metaAddress:104344 && MFTSequence:50) (metaAddress:213316 && MFTSequence:236) (metaAddress:428 && MFTSequence:2) (metaAddress:357303 && MFTSequence:1) (metaAddress:358332 && MFTSequence:1) (metaAddress:441 && MFTSequence:2) (metaAddress:210356 && MFTSequence:12) (metaAddress:357305 && MFTSequence:1) (metaAddress:210865 && MFTSequence:15) (metaAddress:444 && MFTSequence:2) (metaAddress:449 && MFTSequence:2) (metaAddress:357313 && MFTSequence:1) (metaAddress:357315 && MFTSequence:1) (metaAddress:161245 && MFTSequence:3) (metaAddress:75741 && MFTSequence:52) (metaAddress:213297 && MFTSequence:219) (metaAddress:64288 && MFTSequence:206) (metaAddress:64288 && MFTSequence:207) (metaAddress:213297 && MFTSequence:221) (metaAddress:64288 && MFTSequence:208) (metaAddress:64288 && MFTSequence:209) (metaAddress:64288 && MFTSequence:210) (metaAddress:75726 && MFTSequence:60) (metaAddress:3071 && MFTSequence:1) (metaAddress:126326 && MFTSequence:137) )";
    }

}
