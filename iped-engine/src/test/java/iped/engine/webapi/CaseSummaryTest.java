package iped.engine.webapi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import iped.engine.webapi.json.CaseSummaryJSON;

/**
 * Unit tests for the /case-summary contract (design 07e, feature F-5)
 * without a server or an index. Scope note: the aggregation itself
 * (countCategories/modifiedBounds) depends on a live Lucene IndexReader, so
 * it is covered by the HTTP smoke/harness against the reference case
 * (parity with webapi-tests/fixtures/categoria-censo.txt); this class covers
 * the pure logic: the top parameter validation and the whole body assembly
 * (ranking, tie-break, residual flag, synthetic uncategorized,
 * omittedCategories/itemsOutsideTop accounting and period nulling).
 */
public class CaseSummaryTest {

    private static Map<String, Long> counts(Object... pairs) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], ((Number) pairs[i + 1]).longValue());
        }
        return map;
    }

    // ---- top parameter (design 07e E5 / D11) ----

    @Test
    public void topDefaultsToTenWhenAbsentOrBlank() {
        assertEquals(10, CaseSummary.parseTopParam(null));
        assertEquals(10, CaseSummary.parseTopParam(""));
        assertEquals(10, CaseSummary.parseTopParam("   "));
    }

    @Test
    public void topAcceptsBoundariesAndTrims() {
        assertEquals(1, CaseSummary.parseTopParam("1"));
        assertEquals(64, CaseSummary.parseTopParam("64"));
        assertEquals(7, CaseSummary.parseTopParam(" 7 "));
    }

    @Test
    public void topRejectsZeroNegativeOutOfRangeAndGarbage() {
        for (String bad : new String[] { "0", "-3", "65", "abc", "1.5", "", "+", "12a",
                "99999999999999999999" }) {
            if (bad.isEmpty()) {
                continue; // empty means "absent" (default 10), asserted elsewhere
            }
            try {
                CaseSummary.parseTopParam(bad);
                fail("expected IllegalArgumentException for top='" + bad + "'");
            } catch (IllegalArgumentException e) {
                String msg = e.getMessage();
                assertTrue(msg, msg.startsWith("case-summary: invalid 'top' value '"));
                assertTrue(msg, msg.contains("1..64"));
            }
        }
    }

    @Test
    public void topErrorMessageQuotesTheOffendingValue() {
        try {
            CaseSummary.parseTopParam(" 65x ");
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("case-summary: invalid 'top' value '65x' (expected 1..64)", e.getMessage());
        }
    }

    // ---- body assembly (design 07e D2/D3/D5/D7, AC2/AC3/AC8) ----

    @Test
    public void ranksByCountDescAndTiesByNameAsc() {
        CaseSummaryJSON body = CaseSummary.buildBody(Arrays.asList("S"), 260, 4,
                counts("b files", 100L, "a files", 100L, "other x", 50L, "z", 10L), null, null);
        assertEquals(4, body.getCategories().size());
        assertEquals("a files", body.getCategories().get(0).getCategory());
        assertEquals("b files", body.getCategories().get(1).getCategory());
        assertEquals("other x", body.getCategories().get(2).getCategory());
        assertEquals("z", body.getCategories().get(3).getCategory());
        assertEquals(0, body.getOmittedCategories());
        assertEquals(0, body.getItemsOutsideTop());
        assertEquals(4, body.getDistinctCategories());
        assertEquals(260, body.getTotalItems());
    }

    @Test
    public void residualFlagIsLabelPrefixNotSize() {
        CaseSummaryJSON body = CaseSummary.buildBody(Arrays.asList("S"), 100, 10,
                counts("other files", 50L, "others chats", 20L, "other images", 15L,
                        "other texts", 10L, "applications usage", 5L),
                null, null);
        for (CaseSummaryJSON.CategoryCountJSON c : body.getCategories()) {
            boolean expected = c.getCategory().startsWith("other");
            assertEquals(c.getCategory(), expected, c.isResidual());
        }
        assertFalse(body.getCategories().get(body.getCategories().size() - 1).isResidual());
    }

    @Test
    public void emptyBucketsAreNeverListed() {
        CaseSummaryJSON body = CaseSummary.buildBody(Arrays.asList("S"), 100, 10,
                counts("audios", 100L, "videos", 0L, "ghosts", -5L), null, null);
        assertEquals(1, body.getCategories().size());
        assertEquals("audios", body.getCategories().get(0).getCategory());
        assertEquals(1, body.getDistinctCategories());
    }

    @Test
    public void topCutIsAuditable() {
        Map<String, Long> fake = counts("c1", 100L, "c2", 90L, "c3", 80L, "c4", 70L, "c5", 60L);
        CaseSummaryJSON body = CaseSummary.buildBody(Arrays.asList("S"), 400, 2, fake, null, null);
        assertEquals(2, body.getCategories().size());
        // distinctCategories = size of the counted map (5 categories in the
        // fake), NOT the listed top-N slice; per design 07e.
        assertEquals(5, body.getDistinctCategories());
        assertEquals(3, body.getOmittedCategories());
        assertEquals(80 + 70 + 60, body.getItemsOutsideTop());
        assertEquals(2, body.getTop());
    }

    @Test
    public void topLargerThanPresentIsNotAnError() {
        CaseSummaryJSON body = CaseSummary.buildBody(Arrays.asList("S"), 190, 64,
                counts("c1", 100L, "c2", 90L), null, null);
        assertEquals(2, body.getCategories().size());
        assertEquals(0, body.getOmittedCategories());
        assertEquals(0, body.getItemsOutsideTop());
    }

    @Test
    public void uncategorizedIsSynthesizedWhenTotalExceedsSum() {
        CaseSummaryJSON body = CaseSummary.buildBody(Arrays.asList("S"), 1000, 10,
                counts("a", 500L, "b", 100L), null, null);
        assertEquals(3, body.getCategories().size());
        assertEquals("a", body.getCategories().get(0).getCategory());
        assertEquals(CaseSummary.UNCATEGORIZED, body.getCategories().get(1).getCategory());
        assertEquals(400, body.getCategories().get(1).getItems());
        assertTrue(body.getCategories().get(1).isResidual());
        assertEquals("b", body.getCategories().get(2).getCategory());
        // synthetic row is not a taxonomy category (design 07e D7 accounting)
        assertEquals(2, body.getDistinctCategories());
    }

    @Test
    public void noUncategorizedWhenSumReachesOrExceedsTotal() {
        CaseSummaryJSON eq = CaseSummary.buildBody(Arrays.asList("S"), 600, 10,
                counts("a", 500L, "b", 100L), null, null);
        assertEquals(2, eq.getCategories().size());
        CaseSummaryJSON over = CaseSummary.buildBody(Arrays.asList("S"), 500, 10,
                counts("a", 500L, "b", 100L), null, null);
        assertEquals(2, over.getCategories().size());
        assertEquals(0, over.getItemsOutsideTop());
    }

    @Test
    public void truncatedUncategorizedCountsInItemsButNotInOmitted() {
        // tie at the cut: "b" sorts before "uncategorized", so the synthetic
        // row falls outside top=2: its items still weigh in itemsOutsideTop
        // but it never counts as an omitted taxonomy category.
        CaseSummaryJSON body = CaseSummary.buildBody(Arrays.asList("S"), 1000, 2,
                counts("a", 900L, "b", 50L), null, null);
        assertEquals(2, body.getCategories().size());
        assertEquals("a", body.getCategories().get(0).getCategory());
        assertEquals("b", body.getCategories().get(1).getCategory());
        assertEquals(0, body.getOmittedCategories());
        assertEquals(50, body.getItemsOutsideTop());
    }

    @Test
    public void emptyScopeMatchesEdgeCaseE3() {
        CaseSummaryJSON body = CaseSummary.buildBody(Arrays.asList("S"), 0, 10,
                counts(), null, null);
        assertEquals(0, body.getCategories().size());
        assertEquals(0, body.getTotalItems());
        assertEquals(0, body.getDistinctCategories());
        assertEquals(0, body.getOmittedCategories());
        assertEquals(0, body.getItemsOutsideTop());
        assertNull(body.getPeriod());
    }

    @Test
    public void periodIsPresentOnlyWithBothBounds() {
        CaseSummaryJSON with = CaseSummary.buildBody(Arrays.asList("S"), 10, 10,
                counts("a", 10L), "2024-01-01T00:00:00Z", "2024-06-30T23:59:59Z");
        assertEquals("2024-01-01T00:00:00Z", with.getPeriod().getFrom());
        assertEquals("2024-06-30T23:59:59Z", with.getPeriod().getTo());
        assertNull(CaseSummary.buildBody(Arrays.asList("S"), 10, 10, counts("a", 10L), null, null)
                .getPeriod());
        assertNull(CaseSummary.buildBody(Arrays.asList("S"), 10, 10, counts("a", 10L), null,
                "2024-06-30T23:59:59Z").getPeriod());
    }

    @Test
    public void sourcesListIsEchoedAndBodyIsRepeatable() {
        Map<String, Long> fake = counts("other files", 3L, "audios", 1L);
        CaseSummaryJSON a = CaseSummary.buildBody(Arrays.asList("B", "A"), 4, 10, fake, null, null);
        CaseSummaryJSON b = CaseSummary.buildBody(Arrays.asList("B", "A"), 4, 10,
                counts("other files", 3L, "audios", 1L), null, null);
        assertEquals(Arrays.asList("B", "A"), a.getSources());
        assertEquals(a.getTotalItems(), b.getTotalItems());
        assertEquals(a.getCategories().size(), b.getCategories().size());
        for (int i = 0; i < a.getCategories().size(); i++) {
            assertEquals(a.getCategories().get(i).getCategory(), b.getCategories().get(i).getCategory());
            assertEquals(a.getCategories().get(i).getItems(), b.getCategories().get(i).getItems());
            assertEquals(a.getCategories().get(i).isResidual(), b.getCategories().get(i).isResidual());
        }
        assertEquals(a.getItemsOutsideTop(), b.getItemsOutsideTop());
        assertEquals(a.getOmittedCategories(), b.getOmittedCategories());
    }
}
