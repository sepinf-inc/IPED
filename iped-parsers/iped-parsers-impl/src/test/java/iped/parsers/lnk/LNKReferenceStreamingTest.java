package iped.parsers.lnk;

import static org.junit.Assert.*;
import static iped.parsers.lnk.ReferenceFixtures.*;
import java.util.*;
import org.junit.*;
import iped.data.IItemReader;

public class LNKReferenceStreamingTest {
    @BeforeClass public static void origin() throws Exception { assertOrigin(); }
    static final class LazyCandidates implements Iterable<IItemReader> {
        int yielded;
        boolean used;
        final boolean winnerFirst, relative;
        final String validPath;
        LazyCandidates(boolean winnerFirst, boolean relative, String path) {
            this.winnerFirst = winnerFirst; this.relative = relative; this.validPath = path;
        }
        public Iterator<IItemReader> iterator() {
            if (used) throw new AssertionError("Second traversal"); used = true;
            return new Iterator<IItemReader>() {
                Item previous;
                public boolean hasNext() { return yielded < 4096; }
                public IItemReader next() {
                    if (!hasNext()) throw new NoSuchElementException();
                    if (previous != null) assertTrue("Previous candidate was buffered before examination", previous.examined);
                    int index = yielded++;
                    previous = new Item(index + 1, relative && index != 4095 ? validPath + ".bak" : validPath);
                    if (!relative && (winnerFirst ? index != 0 : index != 4095)) previous.length = 99L;
                    return previous.reader;
                }
            };
        }
    }
    @Test public void lazyMftWinnerLast() throws Exception { lazyMft(false); }
    @Test public void scoreTwentyDoesNotStopIteration() throws Exception { lazyMft(true); }
    void lazyMft(boolean first) throws Exception {
        LNKShortcut lnk = mft(); Searcher searcher = new Searcher(); searcher.strict = true;
        LazyCandidates lazy = new LazyCandidates(first, false, "/volume/target.txt");
        searcher.routes.put(mftQuery(lnk), () -> lazy);
        run("stream-mft-" + first, new LNKShortcutParser(), lnk, searcher, shortcut(), "C:\\target.txt", first ? 1 : 4096, "MFT");
        assertEquals(4096, lazy.yielded); assertEquals(0, searcher.searchCalls); assertEquals(1, searcher.iterableCalls);
    }
    @Test public void lazyRelativeRejections() throws Exception {
        LNKShortcut lnk = lnk(8); lnk.setRelativePath("target.txt"); Item link = shortcut();
        String path = relativeTarget(link, lnk.getRelativePath());
        Searcher searcher = new Searcher(); searcher.strict = true;
        LazyCandidates lazy = new LazyCandidates(false, true, path);
        searcher.routes.put(pathQuery(path), () -> lazy);
        run("stream-relative", new LNKShortcutParser(), lnk, searcher, link, "C:\\target.txt", 4096, "RelativePath");
        assertEquals(4096, lazy.yielded); assertEquals(0, searcher.searchCalls);
    }
    @Test public void onlyFirstParentIsRead() throws Exception {
        Searcher searcher = new Searcher(); searcher.strict = true;
        Item target = new Item(1, "/volume/target.txt");
        searcher.route(pathQuery("/target.txt"), target);
        final int[] read = { 0 };
        searcher.routes.put(parentQuery(20), () -> () -> new Iterator<IItemReader>() {
            public boolean hasNext() { return read[0] < 2; }
            public IItemReader next() {
                if (++read[0] > 1) throw new AssertionError("Second parent was fetched");
                return root(20, false).reader;
            }
        });
        run("stream-parent", new LNKShortcutParser(), lnk(0), searcher, shortcut(), "C:\\target.txt", 1, "FullLocalPath");
        assertEquals(1, read[0]); assertEquals(0, searcher.searchCalls); assertEquals(2, searcher.iterableCalls);
    }
}
