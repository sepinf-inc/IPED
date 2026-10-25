package iped.app.ui.rag;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

import iped.engine.rag.LLMProvider.PromptMessage;

public class RAGContextSnapshotTest {

    private RAGContextSnapshot snapshot(String body) {
        RAGContextSnapshot.Builder builder = new RAGContextSnapshot.Builder();
        builder.include("7", "case-A#0", "Synthetic.txt", body, .7f, 0, 12);
        return builder.build();
    }

    @Test
    public void testPromptIsRetainedWholeAndCountedForRetention() {
        RAGContextSnapshot.Builder builder = new RAGContextSnapshot.Builder();
        builder.include("7", "case-A#0", null, "body", 1f, 0, 0);
        builder.prompt(Arrays.asList(new PromptMessage("system", "abc"), new PromptMessage("assistant", null),
                new PromptMessage("user", "Context:\nbody")));
        RAGContextSnapshot result = builder.build();
        assertEquals(3, result.prompt.size());
        assertEquals("user", result.prompt.get(2).role);
        assertEquals(3 + 13, result.promptChars);
        assertEquals(4 + 16, result.retainedChars());
    }

    @Test
    public void testOversizedPromptIsDroppedButFragmentsRemain() {
        RAGContextSnapshot.Builder builder = new RAGContextSnapshot.Builder();
        builder.include("7", "case-A#0", null, "body", 1f, 0, 0);
        builder.prompt(Collections.singletonList(
                new PromptMessage("user", "x".repeat(RAGContextSnapshot.MAX_PROMPT_CHARS + 1))));
        RAGContextSnapshot result = builder.build();
        assertNull(result.prompt);
        assertEquals(0, result.promptChars);
        assertEquals(1, result.fragments.size());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testPromptIsUnmodifiable() {
        RAGContextSnapshot.Builder builder = new RAGContextSnapshot.Builder();
        builder.prompt(new ArrayList<>(Collections.singletonList(new PromptMessage("user", "q"))));
        builder.build().prompt.clear();
    }

    @Test
    public void testCacheLimitIncludesPromptChars() {
        RAGContextSnapshot.Cache cache = new RAGContextSnapshot.Cache(3, 10);
        RAGContextSnapshot.Builder builder = new RAGContextSnapshot.Builder();
        builder.include("7", "case-A#0", null, "123", 1f, 0, 0);
        builder.prompt(Collections.singletonList(new PromptMessage("user", "12345678")));
        cache.put("one", "A", builder.build());
        assertNull(cache.get("one"));
        cache.put("two", "A", snapshot("123"));
        assertNotNull(cache.get("two"));
    }

    @Test
    public void testRawTextOffsetsAndDistinctChunkIdentities() {
        String first = "\ud83d\udce6 Ana\n sent\u00a0the package.";
        String second = "sent\u00a0the package. suffix";
        String context = "header\n" + first + "\nnext\n" + second;
        RAGContextSnapshot.Builder builder = new RAGContextSnapshot.Builder();
        builder.include("7", "case-A#0", "A", first, 1f, 0, 7);
        builder.omit(1, "7", "case-B#0", false);
        builder.include("7", "case-A#1", "A", second, .8f, 2, 7 + first.length() + 6);
        RAGContextSnapshot result = builder.build();
        assertEquals(2, result.fragments.size());
        assertEquals("case-A#1", result.fragments.get(1).contenttrackID);
        assertEquals(2, result.fragments.get(1).retrievedOrdinal);
        assertEquals(1, result.fragments.get(1).includedOrdinal);
        for (RAGContextSnapshot.Fragment f : result.fragments) {
            assertEquals(f.content, context.substring(f.bodyStart, f.bodyEnd));
        }
        assertEquals("case-B#0", result.omissions.get(0).contenttrackID);
        assertFalse(result.omissions.get(0).emptyContent);
    }

    @Test
    public void testSnapshotDoesNotChangeWhenBuilderContinues() {
        RAGContextSnapshot.Builder builder = new RAGContextSnapshot.Builder();
        builder.include("7", "case-A#0", null, "original", 1f, 0, 0);
        RAGContextSnapshot before = builder.build();
        builder.include("7", "case-B#0", null, "later", 1f, 1, 20);
        builder.omit(2, "8", null, true);
        assertEquals(1, before.fragments.size());
        assertEquals("original", before.fragments.get(0).content);
        assertTrue(before.omissions.isEmpty());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testFragmentsAreUnmodifiable() {
        snapshot("body").fragments.clear();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testOmissionsAreUnmodifiable() {
        snapshot("body").omissions.clear();
    }

    @Test
    public void testEmptyAvailableSnapshotDiffersFromUnavailable() {
        assertNotNull(new RAGContextSnapshot.Builder().build());
        RAGContextSnapshot.Builder builder = new RAGContextSnapshot.Builder();
        String body = "x".repeat(RAGContextSnapshot.MAX_BODY_CHARS);
        builder.include("7", "a#0", null, body, 1f, 0, 0);
        assertNotNull(builder.build());
        builder.include("8", "b#0", null, "x", 1f, 1, body.length());
        assertNull(builder.build());
        builder.omit(2, "9", null, true);
        assertNull(builder.build());
    }

    @Test
    public void testMetadataIsBoundedEvenForEmptyBodies() {
        RAGContextSnapshot.Builder builder = new RAGContextSnapshot.Builder();
        for (int i = 0; i < RAGContextSnapshot.MAX_SOURCES; i++) {
            builder.omit(i, "7", null, true);
        }
        assertEquals(RAGContextSnapshot.MAX_SOURCES, builder.build().omissions.size());
        builder.omit(256, "7", null, true);
        assertNull(builder.build());
    }

    @Test
    public void testAggregateEvictionKeepsWholeSnapshots() {
        RAGContextSnapshot.Cache cache = new RAGContextSnapshot.Cache(3, 6);
        cache.put("one", "A", snapshot("123"));
        cache.put("two", "B", snapshot("456"));
        cache.get("one"); // Reads must not change oldest-completion order.
        cache.put("three", "A", snapshot("7"));
        assertNull(cache.get("one"));
        assertNotNull(cache.get("two"));
        assertNotNull(cache.get("three"));
        cache.put("too-big", "A", snapshot("1234567"));
        assertNull(cache.get("too-big"));
        assertNotNull(cache.get("two"));
    }

    @Test
    public void testCountEvictionAndSessionCleanup() {
        RAGContextSnapshot.Cache cache = new RAGContextSnapshot.Cache(2, 100);
        cache.put("one", "A", snapshot("1"));
        cache.put("two", "B", snapshot("2"));
        cache.put("three", "A", snapshot("3"));
        assertNull(cache.get("one"));
        cache.removeSession("A");
        assertNull(cache.get("three"));
        assertNotNull(cache.get("two"));
        cache.clear();
        assertNull(cache.get("two"));
    }

    @Test
    public void testReplacementAndUnavailableDoNotLeakRetentionBudget() {
        RAGContextSnapshot.Cache cache = new RAGContextSnapshot.Cache(2, 3);
        cache.put("one", "A", snapshot("123"));
        cache.put("one", "A", snapshot("1"));
        cache.put("two", "B", snapshot("23"));
        assertNotNull(cache.get("one"));
        assertNotNull(cache.get("two"));
        cache.put("one", "A", null);
        assertNull(cache.get("one"));
        cache.put("three", "C", snapshot("4"));
        assertNotNull(cache.get("two"));
    }
}
