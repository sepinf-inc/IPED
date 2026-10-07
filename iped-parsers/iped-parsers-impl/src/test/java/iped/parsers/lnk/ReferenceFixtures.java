package iped.parsers.lnk;

import static org.junit.Assert.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Supplier;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToXMLContentHandler;
import iped.data.IItemReader;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;
import iped.search.IItemSearcher;
import iped.utils.DateUtil;

/**
 * Synthetic reference-resolution fixtures. No engine, index or external LNK is needed.
 * Optional lnk.trace/lnk.expectedOrigin properties support separate-JVM comparisons;
 * ordinary JUnit execution does not require either property or write any files.
 */
final class ReferenceFixtures {
    static final long EPOCH = 1600000000000L;
    static final Map<String, String> TRACES = new ConcurrentSkipListMap<>();
    static long filetime(long millis) { return millis * 10000L + 116444736000000000L; }

    static final class Item implements InvocationHandler {
        final int id;
        Integer parent = 20;
        String path, name, track;
        Long length = 100L;
        Date created = new Date(EPOCH), modified = new Date(EPOCH);
        boolean deleted, root, examined;
        final IItemReader reader;
        Item(int id, String path) {
            this.id = id; this.path = path;
            this.name = path.substring(Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\')) + 1);
            this.track = "track-" + id;
            reader = (IItemReader) Proxy.newProxyInstance(IItemReader.class.getClassLoader(),
                    new Class<?>[] { IItemReader.class }, this);
        }
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
            case "getId": return id;
            case "getParentId": return parent;
            case "getPath": examined = true; return path;
            case "getName": return name;
            case "getLength": examined = true; return length;
            case "getCreationDate": return created;
            case "getModDate": return modified;
            case "isDeleted": return deleted;
            case "isRoot": return root;
            case "getExtraAttribute":
                assertEquals("Unexpected extra attribute", BasicProps.TRACK_ID, args[0]); return track;
            case "equals": return proxy == args[0];
            case "hashCode": return System.identityHashCode(proxy);
            case "toString": return "synthetic-item-" + id;
            default: throw new AssertionError("Unconfigured IItemReader method: " + method);
            }
        }
    }

    static final class Searcher implements InvocationHandler {
        // Proxy avoids coupling the fixture to optional searcher methods.
        final IItemSearcher reader = (IItemSearcher) Proxy.newProxyInstance(
                IItemSearcher.class.getClassLoader(), new Class<?>[] { IItemSearcher.class }, this);

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
            case "search": return search((String) args[0]);
            case "searchIterable": return searchIterable((String) args[0]);
            case "escapeQuery": return escapeQuery((String) args[0]);
            case "close": return null;
            case "equals": return proxy == args[0];
            case "hashCode": return System.identityHashCode(proxy);
            case "toString": return "synthetic-searcher";
            default: throw new AssertionError("Unconfigured IItemSearcher method: " + method);
            }
        }

        final Map<String, Supplier<Iterable<IItemReader>>> routes = new LinkedHashMap<>();
        final List<String> queries = new ArrayList<>();
        boolean strict = Boolean.getBoolean("lnk.streaming");
        int searchCalls, iterableCalls;
        void route(String query, Item... items) {
            routes.put(query, () -> {
                List<IItemReader> readers = new ArrayList<>();
                for (Item item : items) readers.add(item.reader);
                return readers;
            });
        }
        Iterable<IItemReader> resolve(String query) {
            Supplier<Iterable<IItemReader>> factory = routes.get(query);
            return factory == null ? Collections.emptyList() : factory.get();
        }
        public List<IItemReader> search(String query) {
            searchCalls++;
            if (strict) throw new AssertionError("Eager search called: " + query);
            queries.add(query);
            List<IItemReader> result = new ArrayList<>();
            for (IItemReader reader : resolve(query)) result.add(reader);
            return result;
        }
        public Iterable<IItemReader> searchIterable(String query) {
            iterableCalls++; queries.add(query);
            Iterable<IItemReader> delegate = resolve(query);
            return new Iterable<IItemReader>() {
                boolean used;
                public Iterator<IItemReader> iterator() {
                    if (used) throw new AssertionError("Iterable traversed twice: " + query);
                    used = true; return delegate.iterator();
                }
            };
        }
        public String escapeQuery(String query) { return escaped(query); }
        public void close() { }
    }

    static LNKShortcut lnk(int flags) {
        LNKShortcut lnk = new LNKShortcut();
        lnk.setDataLinkFlags(flags | 2);
        lnk.setLinkLocation(new LNKLinkLocation());
        lnk.setFileSize(100L);
        lnk.setCreateDate(filetime(EPOCH));
        lnk.setModifiedDate(filetime(EPOCH));
        return lnk;
    }
    static LNKShortcut mft() {
        LNKShortcut lnk = lnk(1);
        LNKShellItem shell = new LNKShellItem();
        shell.setFileEntry(100, 0, 0, "target.txt");
        shell.getFileEntry().setIndMft(9);
        shell.getFileEntry().setSeqMft(2);
        lnk.addShellTargetID(shell);
        return lnk;
    }
    static String mftQuery(LNKShortcut lnk) {
        return BasicProps.META_ADDRESS + ":9 && " + BasicProps.MFT_SEQUENCE + ":2 && "
                + BasicProps.CREATED + ":\"" + DateUtil.dateToString(lnk.getCreateDate()) + "\"";
    }
    // A sentinel makes the tests fail if the parser bypasses escapeQuery.
    // No real Lucene query is run; query escaping belongs to the searcher.
    static String escaped(String query) { return "escaped(" + query + ")"; }
    static String pathQuery(String path) { return BasicProps.PATH + ":\"" + escaped(path) + "\""; }
    static String parentQuery(int id) { return BasicProps.ID + ":" + id; }
    static Item shortcut() { return new Item(900, "C:/case/links/shortcut.lnk"); }
    static String relativeTarget(Item shortcut, String relative) {
        return Paths.get(shortcut.path, "..", relative.replace('\\', '/')).normalize().toString();
    }
    static Item root(int id, boolean byFlag) {
        Item root = new Item(id, "/evidence/volume/");
        root.name = byFlag ? "volume-root" : "/";
        root.root = byFlag;
        return root;
    }
    static void fullParents(Searcher searcher) {
        Item folder = new Item(10, "/evidence/volume/demo"); folder.parent = 20;
        searcher.route(parentQuery(10), folder);
        searcher.route(parentQuery(20), root(20, false));
    }
    static ParseContext context(Searcher searcher, Item shortcut) {
        ParseContext ctx = new ParseContext();
        if (searcher != null) ctx.set(IItemSearcher.class, searcher.reader);
        if (shortcut != null) ctx.set(IItemReader.class, shortcut.reader);
        return ctx;
    }
    static IItemReader reference(LNKShortcutParser parser, Metadata metadata, ParseContext ctx,
            LNKShortcut lnk, String fullPath) throws Exception {
        Method method = LNKShortcutParser.class.getDeclaredMethod("makeReference", Metadata.class,
                ParseContext.class, LNKShortcut.class, String.class);
        method.setAccessible(true);
        try { return (IItemReader) method.invoke(parser, metadata, ctx, lnk, fullPath); }
        catch (InvocationTargetException e) {
            if (e.getCause() instanceof Error) throw (Error) e.getCause();
            if (e.getCause() instanceof Exception) throw (Exception) e.getCause();
            throw e;
        }
    }
    static Metadata run(String name, LNKShortcutParser parser, LNKShortcut lnk, Searcher searcher,
            Item shortcut, String fullPath, Integer expected, String strategy) throws Exception {
        Metadata metadata = new Metadata();
        IItemReader selected = reference(parser, metadata, context(searcher, shortcut), lnk, fullPath);
        assertEquals(name, expected, selected == null ? null : Integer.valueOf(selected.getId()));
        assertEquals(name, strategy, metadata.get(LNKShortcutParser.LNK_METADATA_TARGET_REF_STRATEGY));
        assertEquals(name, expected == null ? null : "true",
                metadata.get(LNKShortcutParser.LNK_METADATA_TARGET_REFERENCED));
        record(name, selected, searcher, metadata, "");
        return metadata;
    }
    static void field(StringBuilder out, String value) {
        if (value == null) { out.append("-1:"); return; }
        out.append(value.length()).append(':').append(value);
    }
    static void record(String name, IItemReader selected, Searcher searcher, Metadata metadata, String xml) {
        StringBuilder out = new StringBuilder();
        field(out, selected == null ? null : Integer.toString(selected.getId()));
        List<String> queries = searcher == null ? Collections.emptyList() : searcher.queries;
        out.append(queries.size()).append(';');
        for (String query : queries) field(out, query);
        String[] names = metadata.names(); Arrays.sort(names);
        out.append(names.length).append(';');
        for (String key : names) {
            field(out, key);
            String[] values = metadata.getValues(key); out.append(values.length).append(';');
            for (String value : values) field(out, value);
        }
        field(out, xml);
        String encoded = Base64.getEncoder().encodeToString(out.toString().getBytes(StandardCharsets.UTF_8));
        assertNull("Duplicate scenario: " + name, TRACES.put(name, encoded));
    }
    static void writeTraces() throws IOException {
        String path = System.getProperty("lnk.trace");
        if (path == null) return;
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, String> trace : TRACES.entrySet()) lines.add(trace.getKey() + "\t" + trace.getValue());
        Files.write(Paths.get(path), lines, StandardCharsets.UTF_8);
        System.out.println("BEHAVIOR_SCENARIOS=" + lines.size());
    }
    static void assertOrigin() throws Exception {
        Path actual = Paths.get(LNKShortcutParser.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
        String expected = System.getProperty("lnk.expectedOrigin");
        if (expected == null) return; // Optional standalone comparison harness.
        assertEquals(Paths.get(expected).toRealPath(), actual);
        System.out.println("PARSER_ORIGIN=" + actual);
    }
    static byte[] syntheticLnk() {
        byte[] path = "C:\\demo\\target.txt\0".getBytes(StandardCharsets.US_ASCII);
        int linkInfoSize = 28 + 17 + path.length + 1;
        ByteBuffer buffer = ByteBuffer.allocate(76 + linkInfoSize + 4).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(76);
        buffer.put(new byte[] { 1, 20, 2, 0, 0, 0, 0, 0, (byte) 192, 0, 0, 0, 0, 0, 0, 70 });
        buffer.putInt(2).putInt(0).putLong(filetime(EPOCH)).putLong(filetime(EPOCH)).putLong(filetime(EPOCH));
        buffer.putInt(100).putInt(0).putInt(1).putShort((short) 0).putShort((short) 0).putInt(0).putInt(0);
        assertEquals(76, buffer.position());
        buffer.putInt(linkInfoSize).putInt(28).putInt(1).putInt(28).putInt(45).putInt(0).putInt(45 + path.length);
        buffer.putInt(17).putInt(3).putInt(123).putInt(16).put((byte) 0);
        buffer.put(path).put((byte) 0).putInt(0);
        return buffer.array();
    }
    static void publicParse(LNKShortcutParser parser) throws Exception {
        Searcher searcher = new Searcher(); fullParents(searcher);
        Item target = new Item(1, "/evidence/volume/demo/target.txt"); target.parent = 10;
        searcher.route(pathQuery("/demo/target.txt"), target);
        Metadata metadata = new Metadata(); ToXMLContentHandler handler = new ToXMLContentHandler();
        parser.parse(new ByteArrayInputStream(syntheticLnk()), handler, metadata, context(searcher, shortcut()));
        assertEquals("true", metadata.get(LNKShortcutParser.LNK_METADATA_TARGET_REFERENCED));
        assertEquals("FullLocalPath", metadata.get(LNKShortcutParser.LNK_METADATA_TARGET_REF_STRATEGY));
        assertEquals(BasicProps.TRACK_ID + ":track-1", metadata.get(ExtraProperties.LINKED_ITEMS));
        assertTrue(handler.toString().contains("target.txt"));
        assertEquals(Arrays.asList(pathQuery("/demo/target.txt"), parentQuery(10), parentQuery(20)), searcher.queries);
        record("public-parse", target.reader, searcher, metadata, handler.toString());
    }
}
