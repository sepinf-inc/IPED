package iped.parsers.leveldb;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import iped.data.IItemReader;
import iped.parsers.leveldb.model.LDB4jReader;
import iped.parsers.leveldb.model.Manifest;
import iped.parsers.leveldb.model.Record;
import iped.parsers.leveldb.model.RecordSink;
import iped.parsers.standard.StandardParser;
import iped.properties.BasicProps;
import iped.search.IItemSearcher;

/**
 * Optional IPED integration adapter for the independently tested LevelDbReader.
 * Integration must be compiled and smoke-tested in the target IPED checkout.
 * Queue ordering (engine-side) is required for whole-database mode: register
 * CURRENT in QueuesProcessingOrder with priority 2; other LevelDB constituents
 * stay in queue 0. This lets IItemSearcher find their indexed items when
 * CURRENT is parsed. No separate post-processing pass is required. Do not
 * enable both modes simultaneously unless duplicated subitems are desired.
 */
public final class LevelDbParser extends AbstractParser {
    private static final long serialVersionUID = 1L;
    public static final MediaType TABLE = MediaType.application("x-leveldb-sstable");
    public static final MediaType CURRENT = MediaType.application("x-leveldb-current");
    public static final MediaType RECORD = MediaType.application("x-leveldb-record");

    public static final String METADATA_PERFIX = "leveldb";

    private static final Set<MediaType> TYPES = MediaType.set(TABLE, CURRENT);
    private static final Pattern DATA_FILE = Pattern.compile("[0-9]+\\.(?:ldb|sst|log)");
    private static final Pattern MANIFEST = Pattern.compile("MANIFEST-[0-9]+");

    private boolean extractStandalone = true;
    private boolean extractDatabase = true;
    private int maxRecords = 1000000;
    private int maxSiblingFiles = 20000;

    @Field
    public void setExtractStandalone(boolean value) {
        extractStandalone = value;
    }

    @Field
    public void setExtractDatabase(boolean value) {
        extractDatabase = value;
    }

    @Field
    public void setMaxRecords(int value) {
        maxRecords = value;
    }

    @Field
    public void setMaxSiblingFiles(int value) {
        maxSiblingFiles = value;
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return TYPES;
    }

    @Override
    public void parse(InputStream input, ContentHandler handler, Metadata metadata, ParseContext context) throws IOException, SAXException, TikaException {
        String mime = metadata.get(StandardParser.INDEXER_CONTENT_TYPE);
        if (mime == null)
            mime = metadata.get(Metadata.CONTENT_TYPE);
        boolean full = CURRENT.toString().equals(mime);
        if (full) {
            IItemReader currentItem = context.get(IItemReader.class);
            if (currentItem != null && !"CURRENT".equals(currentItem.getName())) {
                // Detection of MANIFEST-* content alone does not prove that
                // this is the LevelDB CURRENT control file.
                return;
            }
        }
        if (full ? !extractDatabase : !extractStandalone)
            return;

        EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class, new ParsingEmbeddedDocumentExtractor(context));
        IItemReader item = context.get(IItemReader.class);
        Path tmp = Files.createTempDirectory("iped-leveldb-");
        final Map<String, String> evidencePaths = new HashMap<>();
        final Map<String, Integer> evidenceIds = new HashMap<>();
        try {
            if (full) {
                if (item == null || item.getParentId() == null)
                    throw new TikaException("LevelDB full mode requires current IItemReader with parentId");
                IItemSearcher searcher = context.get(IItemSearcher.class);
                if (searcher == null)
                    throw new TikaException("LevelDB full mode requires IItemSearcher (run CURRENT in processing queue 2)");
                byte[] current = readLimited(input, 4096);
                String name = new String(current, StandardCharsets.US_ASCII).trim();
                if (!MANIFEST.matcher(name).matches())
                    throw new TikaException("Not a LevelDB CURRENT file");
                Files.write(tmp.resolve("CURRENT"), current);
                evidencePaths.put("CURRENT", item.getPath());
                evidenceIds.put("CURRENT", item.getId());
                stageSiblings(item, searcher, tmp, evidencePaths, evidenceIds);
            } else {
                String name = item == null ? metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY) : item.getName();
                if (name == null || !(name.toLowerCase().endsWith(".ldb") || name.toLowerCase().endsWith(".sst")))
                    name = "000000.ldb";
                if (name.indexOf('/') >= 0 || name.indexOf('\\') >= 0 || name.equals(".."))
                    throw new IOException("Bad LevelDB table name");
                Path staged = tmp.resolve(name);
                Files.copy(input, staged);
                if (!LDB4jReader.looksLikeTable(staged))
                    throw new TikaException("Not a valid complete LevelDB SSTable (invalid footer)");
                evidencePaths.put(name, item == null ? name : item.getPath());
                if (item != null)
                    evidenceIds.put(name, item.getId());
            }
            XHTMLContentHandler html = new XHTMLContentHandler(handler, metadata);
            html.startDocument();
            html.element("h2", full ? "LevelDB forensic database records" : "LevelDB forensic SSTable records");
            final int[] count = { 0 };
            RecordSink sink = new RecordSink() {
                @Override
                public void onRecord(Record record) throws IOException {
                    if (maxRecords > 0 && count[0] >= maxRecords)
                        throw new IOException("LevelDB maxRecords reached; increase setting to prevent truncation");
                    count[0]++;
                    String filename = record.source.getFileName().toString();
                    String original = evidencePaths.getOrDefault(filename, filename);
                    String readableKey = printableUtf8(record.key);

                    Metadata child = new Metadata();
                    if (readableKey != null) {
                        child.set(TikaCoreProperties.RESOURCE_NAME_KEY, readableKey);
                    } else {
                        child.set(TikaCoreProperties.RESOURCE_NAME_KEY, "LevelDB record " + count[0] + " (seq " + Long.toUnsignedString(record.sequence) + ")");
                    }

                    // Raw original value bytes are passed to the IPED subitem extractor.
                    child.set(StandardParser.INDEXER_CONTENT_TYPE, RECORD.toString());
                    child.set(METADATA_PERFIX + ":originPath", original);
                    Integer originalId = evidenceIds.get(filename);
                    if (originalId != null)
                        child.set(METADATA_PERFIX + ":sourceItemId", Integer.toString(originalId));
                    child.set(METADATA_PERFIX + ":operation", record.operation);
                    child.set(METADATA_PERFIX + ":sequence", Long.toUnsignedString(record.sequence));
                    child.set(METADATA_PERFIX + ":sourceType", record.sourceType);
                    child.set(METADATA_PERFIX + ":blockOffset", Long.toString(record.blockOffset));
                    child.set(METADATA_PERFIX + ":entryOffset", Long.toString(record.entryOffset));
                    child.set(METADATA_PERFIX + ":compressed", Boolean.toString(record.compressed));
                    if (record.manifestActive != null)
                        child.set(METADATA_PERFIX + ":manifestActive", record.manifestActive.toString());
                    if (record.level != null)
                        child.set(METADATA_PERFIX + ":level", record.level.toString());
                    // Exact key base64 for reasonable-sized keys; never silently truncate.
                    if (record.key.length <= 65536)
                        child.set(METADATA_PERFIX + ":keyBase64", Base64.getEncoder().encodeToString(record.key));
                    else
                        child.set(METADATA_PERFIX + ":keyTooLargeForMetadata", Integer.toString(record.key.length));
                    if (readableKey != null)
                        child.set(METADATA_PERFIX + ":keyPreview", readableKey);
                    try {
                        // Avoid injecting huge values into parent text; subitem receives full bytes.
                        String valPreview = printableUtf8(record.value);
                        html.element("p", "Source: " + filename + "; seq=" + Long.toUnsignedString(record.sequence) + "; op=" + record.operation + "; key="
                                + (readableKey == null ? "[binary]" : readableKey) + (valPreview == null ? "" : "; value=" + valPreview));
                        if (extractor.shouldParseEmbedded(child)) {
                            extractor.parseEmbedded(new ByteArrayInputStream(record.value), handler, child, false);
                        }
                    } catch (SAXException ex) {
                        throw new IOException("Subitem extraction failed at record " + count[0], ex);
                    }
                }

                @Override
                public void onWarning(String w) throws IOException {
                    try {
                        html.element("p", "WARNING: " + w);
                    } catch (SAXException ex) {
                        throw new IOException(ex);
                    }
                }

                @Override
                public void onManifest(Manifest m) throws IOException {
                    try {
                        html.element("p", "MANIFEST edits=" + m.edits + ", referenced tables=" + m.activeTables.size());
                    } catch (SAXException ex) {
                        throw new IOException(ex);
                    }
                }
            };
            try {
                if (full)
                    LDB4jReader.readDirectory(tmp, sink);
                else
                    LDB4jReader.readSingle(tmp.resolve(evidencePaths.keySet().iterator().next()), sink);
            } finally {
                html.endDocument();
            }
            metadata.set(METADATA_PERFIX + ":recordCount", Integer.toString(count[0]));
        } finally {
            deleteTree(tmp);
        }
    }

    private void stageSiblings(IItemReader current, IItemSearcher searcher, Path directory, Map<String, String> evidencePaths, Map<String, Integer> evidenceIds) throws IOException {
        String path = current.getPath();
        int slash = path.lastIndexOf('/');
        if (slash < 0)
            throw new IOException("Cannot determine LevelDB directory from item path: " + path);
        String parent = path.substring(0, slash);
        Integer parentId = current.getParentId();
        int seen = 0;
        // Exact parentId search, with path validation. CURRENT must be in queue 2;
        // all relevant sibling files must remain in queue 0. The queue transition
        // occurs only after all queue 0 items have finished processing.
        for (IItemReader sibling : searcher.searchIterable(BasicProps.PARENTID + ":" + parentId)) {
            if (!parentId.equals(sibling.getParentId()) || sibling.isDir())
                continue;
            String sp = sibling.getPath();
            if (sp == null || sp.lastIndexOf('/') < 0 || !sp.substring(0, sp.lastIndexOf('/')).equals(parent))
                continue;
            String name = sibling.getName();
            if (name == null || !(MANIFEST.matcher(name).matches() || DATA_FILE.matcher(name).matches()))
                continue;
            if (++seen > maxSiblingFiles)
                throw new IOException("Too many LevelDB sibling files; configured limit reached");
            if (evidencePaths.containsKey(name))
                throw new IOException("Duplicate LevelDB sibling filename; ambiguous evidence: " + name);
            try (InputStream is = sibling.getBufferedInputStream()) {
                Files.copy(is, directory.resolve(name));
            }
            evidencePaths.put(name, sibling.getPath());
            evidenceIds.put(name, sibling.getId());
        }
        String manifestName = new String(Files.readAllBytes(directory.resolve("CURRENT")), StandardCharsets.US_ASCII).trim();
        if (!Files.isRegularFile(directory.resolve(manifestName))) {
            throw new IOException("CURRENT MANIFEST unavailable among indexed siblings: " + manifestName + "; verify CURRENT is in processing queue 2 and siblings are indexed in queue 0");
        }
    }

    private static String printableUtf8(byte[] raw) {
        try {
            String s = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(raw, 0, Math.min(raw.length, 2048))).toString();
            if (s.length() > 512)
                s = s.substring(0, 512) + "…";
            return s.replaceAll("[\\p{Cntrl}&&[^\\n\\r\\t]]", " ");
        } catch (CharacterCodingException ex) {
            return null;
        }
    }

    private static byte[] readLimited(InputStream in, int limit) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] b = new byte[4096];
        int n;
        while ((n = in.read(b)) != -1) {
            if (out.size() + n > limit)
                throw new IOException("CURRENT too large");
            out.write(b, 0, n);
        }
        return out.toByteArray();
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root))
            return;
        try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
            for (Path path : (Iterable<Path>) paths.sorted(java.util.Comparator.reverseOrder())::iterator)
                Files.deleteIfExists(path);
        }
    }
}
