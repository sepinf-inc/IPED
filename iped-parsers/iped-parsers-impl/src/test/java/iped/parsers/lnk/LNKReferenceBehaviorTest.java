package iped.parsers.lnk;

import static org.junit.Assert.*;
import static iped.parsers.lnk.ReferenceFixtures.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.*;
import org.apache.tika.metadata.Metadata;
import iped.data.IItemReader;
import iped.properties.BasicProps;
import iped.properties.ExtraProperties;

public class LNKReferenceBehaviorTest {
    final LNKShortcutParser parser = new LNKShortcutParser();
    @BeforeClass public static void origin() throws Exception { assertOrigin(); }
    @AfterClass public static void traces() throws Exception { writeTraces(); }

    @Test public void preconditions() throws Exception {
        run("no-searcher", parser, lnk(0), null, shortcut(), "C:\\target.txt", null, null);
        Searcher empty = new Searcher();
        run("no-target", parser, lnk(0), empty, shortcut(), "C:\\target.txt", null, null);
        assertEquals(Collections.singletonList(pathQuery("/target.txt")), empty.queries);
        for (String share : new String[] { "", "\\\\server\\share" }) {
            LNKShortcut lnk = mft(); lnk.getLinkLocation().setNetShare(share);
            Searcher searcher = new Searcher();
            run("network-" + share.length(), parser, lnk, searcher, shortcut(), "C:\\target.txt", null, null);
            assertTrue(searcher.queries.isEmpty());
        }
    }

    @Test public void strategyPriorityAndFallback() throws Exception {
        LNKShortcut lnk = mft(); lnk.setDataLinkFlags(11); lnk.setRelativePath("target.txt");
        Item link = shortcut();
        String relative = relativeTarget(link, lnk.getRelativePath());
        Item mftTarget = new Item(1, "/another-volume/target.txt");
        Item relativeItem = new Item(2, relative);
        Item full = new Item(3, "/evidence/volume/demo/target.txt"); full.parent = 10;
        for (int stage = 0; stage < 3; stage++) {
            Searcher searcher = new Searcher(); fullParents(searcher);
            if (stage == 0) searcher.route(mftQuery(lnk), mftTarget);
            if (stage < 2) searcher.route(pathQuery(relative), relativeItem);
            searcher.route(pathQuery("/demo/target.txt"), full);
            String strategy = new String[] { "MFT", "RelativePath", "FullLocalPath" }[stage];
            run("priority-" + stage, parser, lnk, searcher, link, "C:\\demo\\target.txt", stage + 1, strategy);
            List<String> queries = new ArrayList<>(); queries.add(mftQuery(lnk));
            if (stage > 0) queries.add(pathQuery(relative));
            if (stage > 1) queries.addAll(Arrays.asList(pathQuery("/demo/target.txt"), parentQuery(10), parentQuery(20)));
            assertEquals(queries, searcher.queries);
        }
    }

    @Test public void mftAndRelativePreconditions() throws Exception {
        for (int variant = 0; variant < 9; variant++) {
            LNKShortcut lnk = mft(); Item link = shortcut();
            switch (variant) {
            case 0: link = null; break;
            case 1: lnk.setDataLinkFlags(2); break;
            case 2: lnk.getShellTargetIDList().clear(); break;
            case 3: lnk.setCreateDate(0); break;
            case 4: lnk.getShellTargetIDList().clear(); lnk.addShellTargetID(new LNKShellItem()); break;
            case 5: lnk.getShellTargetIDList().get(0).getFileEntry().setIndMft(-1); break;
            case 6: lnk.getShellTargetIDList().get(0).getFileEntry().setSeqMft(-1); break;
            case 7: lnk.setDataLinkFlags(10); lnk.setRelativePath(null); break;
            case 8: lnk.setDataLinkFlags(10); lnk.setRelativePath("target.txt"); link = null; break;
            default: throw new AssertionError();
            }
            Searcher searcher = new Searcher();
            Item full = new Item(1, "/evidence/volume/target.txt");
            searcher.route(pathQuery("/target.txt"), full);
            searcher.route(parentQuery(20), root(20, false));
            run("precondition-" + variant, parser, lnk, searcher, link, "C:\\target.txt", 1, "FullLocalPath");
            assertEquals(Arrays.asList(pathQuery("/target.txt"), parentQuery(20)), searcher.queries);
        }
    }

    @Test public void relativeExactPaths() throws Exception {
        String[] relatives = { "target.txt", "..\\demo\\target.txt", "../demo/target.txt", "..\\demo\\a b+(x).txt" };
        for (int variant = 0; variant < relatives.length; variant++) {
            LNKShortcut lnk = lnk(8); lnk.setRelativePath(relatives[variant]);
            Item link = shortcut(); String expectedPath = relativeTarget(link, relatives[variant]);
            Searcher searcher = new Searcher();
            Item valid = new Item(3, expectedPath);
            searcher.route(pathQuery(expectedPath), new Item(1, expectedPath + ".bak"),
                    new Item(2, expectedPath.toUpperCase(Locale.ROOT)), valid);
            run("relative-" + variant, parser, lnk, searcher, link, "C:\\target.txt", 3, "RelativePath");
            assertEquals(Collections.singletonList(pathQuery(expectedPath)), searcher.queries);
        }
        LNKShortcut lnk = lnk(8); lnk.setRelativePath("target.txt");
        Item link = shortcut(); Searcher rejected = new Searcher();
        String expectedPath = relativeTarget(link, lnk.getRelativePath());
        rejected.route(pathQuery(expectedPath), new Item(1, expectedPath + ".bak"));
        run("relative-all-rejected", parser, lnk, rejected, link, "C:\\target.txt", null, null);
        assertEquals(Arrays.asList(pathQuery(expectedPath), pathQuery("/target.txt")), rejected.queries);
    }

    @Test public void fullPathRootsAndParentOrdering() throws Exception {
        for (int variant = 0; variant < 6; variant++) {
            Searcher searcher = new Searcher(); fullParents(searcher);
            Item good = new Item(3, "/evidence/volume/demo/target.txt"); good.parent = 10;
            Item wrongSuffix = new Item(1, "/evidence/volume/demo/target.txt.bak"); wrongSuffix.parent = 88;
            Item tooDeep = new Item(2, "/evidence/volume/deeper/demo/target.txt"); tooDeep.parent = 30;
            Item folder = new Item(30, "/evidence/volume/deeper/demo"); folder.parent = 40;
            Item deeper = new Item(40, "/evidence/volume/deeper");
            searcher.route(parentQuery(30), folder); searcher.route(parentQuery(40), deeper);
            searcher.route(pathQuery("/demo/target.txt"), wrongSuffix, tooDeep, good);
            if (variant == 1) searcher.route(parentQuery(20), root(20, true));
            if (variant == 2) searcher.route(parentQuery(10));
            if (variant == 3) searcher.route(parentQuery(20), root(20, false), deeper);
            if (variant == 4) searcher.route(parentQuery(20), deeper, root(20, false));
            if (variant == 5) { good.parent = 99; searcher.route(parentQuery(99)); }
            boolean selected = variant == 0 || variant == 1 || variant == 3;
            run("root-" + variant, parser, lnk(0), searcher, shortcut(), "C:\\demo\\target.txt",
                    selected ? 3 : null, selected ? "FullLocalPath" : null);
            assertFalse("Suffix rejected before parent lookup", searcher.queries.contains(parentQuery(88)));
        }
    }

    @Test public void unitRootAndPrefixes() throws Exception {
        String[] inputs = { "C:\\", "C:\\target.txt", "file://C:\\target.txt" };
        for (int variant = 0; variant < inputs.length; variant++) {
            Searcher searcher = new Searcher();
            String suffix = variant == 0 ? "/" : "/target.txt";
            Item target = new Item(1, "/evidence/volume" + suffix);
            searcher.route(pathQuery(suffix), target);
            searcher.route(parentQuery(20), root(20, false));
            run("drive-" + variant, parser, lnk(0), searcher, shortcut(), inputs[variant], 1, "FullLocalPath");
            assertEquals(Arrays.asList(pathQuery(suffix), parentQuery(20)), searcher.queries);
        }
    }

    @Test public void scoreMatrixAndStableTies() throws Exception {
        Method score = LNKShortcutParser.class.getDeclaredMethod("getItemScore", LNKShortcut.class, IItemReader.class);
        score.setAccessible(true);
        LNKShortcut lnk = mft(); int sequence = 0;
        for (int dateKind = 0; dateKind < 3; dateKind++) {
            for (boolean deleted : new boolean[] { false, true }) {
                for (boolean sameSize : new boolean[] { false, true }) {
                    Item target = new Item(1, "/volume/target.txt"); target.deleted = deleted;
                    target.length = sameSize ? 100L : 99L;
                    target.created = dateKind == 0 ? new Date(EPOCH + 500) : dateKind == 1 ? new Date(EPOCH + 1000) : null;
                    int expected = dateKind == 0 ? (deleted ? 18 : 20) : dateKind == 1 ? (deleted ? 14 : 16) : (deleted ? 8 : 10);
                    if (!sameSize) expected--;
                    assertEquals(expected, ((Integer) score.invoke(parser, lnk, target.reader)).intValue());
                    Searcher searcher = new Searcher(); searcher.route(mftQuery(lnk), target);
                    run("score-" + sequence++, parser, lnk, searcher, shortcut(), "C:\\target.txt", 1, "MFT");
                }
            }
        }
        for (int variant = 0; variant < 4; variant++) {
            Item first = new Item(1, "/first/target.txt"); Item last = new Item(2, "/last/target.txt");
            if (variant == 0) first.length = 99L;
            if (variant == 1) first.length = null;
            if (variant == 2) first.deleted = true;
            Searcher searcher = new Searcher(); searcher.route(mftQuery(lnk), first, last);
            run("winner-" + variant, parser, lnk, searcher, shortcut(), "C:\\target.txt", variant == 3 ? 1 : 2, "MFT");
        }
        lnk.setCreateDate(0);
        Item candidate = new Item(1, "/volume/target.txt");
        assertEquals(10, ((Integer) score.invoke(parser, lnk, candidate.reader)).intValue());
        candidate.deleted = true; candidate.length = null;
        assertEquals(7, ((Integer) score.invoke(parser, lnk, candidate.reader)).intValue());
        TRACES.put("score-null-lnk-date", "10,7");
    }

    @Test public void metadataDifferencesAndMissingTrackId() throws Exception {
        for (int variant = 0; variant < 3; variant++) {
            LNKShortcut lnk = mft(); Item target = new Item(1, "/volume/target.txt");
            if (variant == 0) {
                target.created = new Date(EPOCH + 1000); target.modified = new Date(EPOCH + 2000);
                target.length = 99L; target.name = "different.txt";
            }
            if (variant == 1) { target.created = new Date(EPOCH + 999); target.modified = new Date(EPOCH + 999); }
            if (variant == 2) target.track = null;
            Searcher searcher = new Searcher(); searcher.route(mftQuery(lnk), target);
            Metadata meta = run("metadata-" + variant, parser, lnk, searcher, shortcut(), "C:\\target.txt", 1, "MFT");
            assertArrayEquals(variant == 0 ? new String[] { BasicProps.CREATED, BasicProps.MODIFIED, BasicProps.LENGTH, BasicProps.NAME }
                    : new String[0], meta.getValues(LNKShortcutParser.LNK_METADATA_TARGET_METADATA_DIFFERENT));
            assertEquals(variant == 2 ? null : BasicProps.TRACK_ID + ":track-1", meta.get(ExtraProperties.LINKED_ITEMS));
        }
    }

    @Test public void publicParseRegression() throws Exception { publicParse(parser); }

    @Test public void concurrentReferences() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(14);
        CountDownLatch ready = new CountDownLatch(14), start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int thread = 0; thread < 14; thread++) {
                final int number = thread;
                futures.add(pool.submit(() -> {
                    ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS));
                    LNKShortcut lnk = mft(); Searcher searcher = new Searcher();
                    Item low = new Item(100 + number, "/volume/target.txt"); low.deleted = true;
                    Item high = new Item(200 + number, "/volume/target.txt");
                    searcher.route(mftQuery(lnk), low, high);
                    run("concurrent-" + number, parser, lnk, searcher, shortcut(), "C:\\target.txt", 200 + number, "MFT");
                    return null;
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            for (Future<?> future : futures) future.get(20, TimeUnit.SECONDS);
        } finally { start.countDown(); pool.shutdownNow(); }
    }

    @Test public void publicInterfaceUnchanged() throws Exception {
        List<String> signatures = new ArrayList<>();
        for (Method method : LNKShortcutParser.class.getDeclaredMethods())
            if (Modifier.isPublic(method.getModifiers())) signatures.add(method.toGenericString());
        for (Field field : LNKShortcutParser.class.getDeclaredFields())
            if (Modifier.isPublic(field.getModifiers())) signatures.add(field.toGenericString() + "=" + field.get(null));
        Collections.sort(signatures);
        Field serial = LNKShortcutParser.class.getDeclaredField("serialVersionUID"); serial.setAccessible(true);
        assertEquals(-3156133141331973368L, serial.getLong(null));
        TRACES.put("public-interface", Base64.getEncoder().encodeToString(String.join("\n", signatures).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
