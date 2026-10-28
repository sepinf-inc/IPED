package iped.app.ui.rag;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class RAGPassageSearchTest {

    private static RAGContextSnapshot snapshot(String... bodies) {
        RAGContextSnapshot.Builder builder = new RAGContextSnapshot.Builder();
        for (int i = 0; i < bodies.length; i++) {
            builder.include("7", "case-" + i + "#0", "synthetic", bodies[i], 1, i, 100 * i);
        }
        return builder.build();
    }

    private static RAGPassageSearch.Result find(RAGContextSnapshot snapshot, String query) {
        return RAGPassageSearch.find(snapshot, query, () -> false, 10000000000L);
    }

    @Test
    public void testWhitespaceRoundtripAndUnicodeOffsets() {
        String body = "\ud83d\udce6 Ana\n\t sent\u00a0the\r\npackage.\n";
        RAGPassageSearch.Result result = find(snapshot(body), "Ana sent the package.");
        assertEquals(RAGPassageSearch.State.COMPLETE, result.state);
        assertEquals(1, result.hits.size());
        RAGPassageSearch.Hit hit = result.hits.get(0);
        assertEquals(3, hit.start);
        assertEquals("Ana\n\t sent\u00a0the\r\npackage.", body.substring(hit.start, hit.end));
        assertEquals(1, find(snapshot(body), "\ud83d\udce6").hits.size());
    }

    @Test
    public void testOverlappingOccurrencesAndCrossCaseDuplicatesRemainSeparate() {
        RAGContextSnapshot snapshot = snapshot("aaa", "aaa");
        RAGPassageSearch.Result result = find(snapshot, "aa");
        assertEquals(4, result.hits.size());
        assertEquals(0, result.hits.get(0).fragmentOrdinal);
        assertEquals(1, result.hits.get(1).start);
        assertEquals(1, result.hits.get(2).fragmentOrdinal);
        assertFalse(snapshot.fragments.get(0).contenttrackID.equals(snapshot.fragments.get(1).contenttrackID));
    }

    @Test
    public void testSameParentChunksAndHeaderLikeTextAreNotReparsed() {
        RAGContextSnapshot.Builder builder = new RAGContextSnapshot.Builder();
        String content = "[7]\nScore H: 1\n---\nLiteral text";
        builder.include("7", "same-case#1", "synthetic", content, 1, 0, 0);
        builder.include("7", "same-case#2", "synthetic", content, 1, 1, 50);
        builder.omit(2, "8", "omitted#0", false);
        assertEquals(2, find(builder.build(), "[7]").hits.size());
        assertTrue(find(builder.build(), "omitted").hits.isEmpty());
    }

    @Test
    public void testLiteralMatchingDoesNotClaimSemanticVerification() {
        assertEquals(1, find(snapshot("Ana did not send the package."), "send the package").hits.size());
        assertTrue(find(snapshot("2026-10-21"), "yesterday").hits.isEmpty());
        assertTrue(find(snapshot("Ana"), "ana").hits.isEmpty());
        assertTrue(find(snapshot("caf\u00e9"), "cafe").hits.isEmpty());
        assertTrue(find(snapshot("caf\u00e9"), "cafe\u0301").hits.isEmpty());
    }

    @Test
    public void testMissingEmptyAndBlankRemainDistinct() {
        assertEquals(RAGPassageSearch.State.UNAVAILABLE, find(null, "x").state);
        assertEquals(RAGPassageSearch.State.COMPLETE, find(snapshot(), "x").state);
        assertEquals(RAGPassageSearch.State.EMPTY_QUERY, find(snapshot("text"), " \t\u00a0").state);
    }

    @Test
    public void testLongOrMalformedExpressionIsRejectedWithoutTruncation() {
        assertEquals(RAGPassageSearch.State.INVALID_QUERY, find(snapshot("x"), "x".repeat(1025)).state);
        assertEquals(RAGPassageSearch.State.INVALID_QUERY, find(snapshot("x"), "\ud83d").state);
        assertEquals(RAGPassageSearch.State.INVALID_QUERY, find(snapshot("x"), "\udce6").state);
        assertEquals(RAGPassageSearch.State.INVALID_QUERY, find(snapshot("x"), null).state);
        assertEquals(1, find(snapshot("x".repeat(1024)), "x".repeat(1024)).hits.size());
    }

    @Test
    public void testHitCapIsGlobalAndExactCapCanStillBeComplete() {
        RAGPassageSearch.Result result = find(snapshot("x".repeat(40), "x".repeat(40)), "x");
        assertEquals(RAGPassageSearch.State.MATCH_LIMIT, result.state);
        assertEquals(64, result.hits.size());
        assertEquals(1, result.hits.get(63).fragmentOrdinal);
        assertEquals(RAGPassageSearch.State.COMPLETE, find(snapshot("x".repeat(64)), "x").state);
    }

    @Test
    public void testCancellationDuringMappingAndExpiredBudget() {
        AtomicInteger polls = new AtomicInteger();
        RAGPassageSearch.Result result = RAGPassageSearch.find(snapshot("x".repeat(20000)), "x",
                () -> polls.incrementAndGet() > 6, 10000000000L);
        assertEquals(RAGPassageSearch.State.CANCELLED, result.state);
        assertTrue(result.hits.isEmpty());
        assertEquals(RAGPassageSearch.State.TIME_LIMIT,
                RAGPassageSearch.find(snapshot("x"), "x", () -> false, 0).state);
    }

    @Test
    public void testPartialCancelledResultsAreNotComplete() {
        AtomicInteger polls = new AtomicInteger();
        RAGPassageSearch.Result result = RAGPassageSearch.find(snapshot("xxxxxxxx"), "x",
                () -> polls.incrementAndGet() > 8, 10000000000L);
        assertEquals(RAGPassageSearch.State.CANCELLED, result.state);
        assertFalse(result.hits.isEmpty());
    }

    @Test
    public void testThreadInterruptIsPreserved() {
        Thread.currentThread().interrupt();
        try {
            assertEquals(RAGPassageSearch.State.CANCELLED, find(snapshot("x"), "x").state);
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testResultListIsImmutable() {
        find(snapshot("x"), "x").hits.clear();
    }
}
