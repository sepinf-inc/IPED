package iped.engine.webapi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

/**
 * Unit tests for {@link Search#buildStructuredQuery} (design 06c, feature F-2).
 * The method is package-private on purpose (testable without a server); these
 * tests pin the composed Lucene query for every filter combination, the
 * leading-dot stripping of ext, the inclusive modified-date range built
 * exclusively from parsed LocalDate values, the quote/escape behaviour
 * (QueryBuilder.escape) and the 400-mapped IllegalArgumentException contract
 * for malformed dates and reversed ranges. No HTTP server or index is needed.
 */
public class SearchQueryBuildTest {

    // ---- no filters: byte-identical to the legacy behaviour (AC of 06c) ----

    @Test
    public void noFiltersReturnsQueryUnchanged() {
        assertEquals("trafico",
                Search.buildStructuredQuery("trafico", null, null, null, null, null));
    }

    @Test
    public void nullQueryWithoutFiltersIsEmpty() {
        assertEquals("", Search.buildStructuredQuery(null, null, null, null, null, null));
    }

    @Test
    public void slashReplacementStillAppliesWithoutFilters() {
        // legacy behaviour kept byte-identical: '/' -> '\/'
        assertEquals("a\\/b", Search.buildStructuredQuery("a/b", null, null, null, null, null));
    }

    // ---- category: single value, repeatable with OR, trimmed, blanks ignored ----

    @Test
    public void singleCategoryBuildsQuotedClause() {
        assertEquals("category:\"whatsapp\"", Search.buildStructuredQuery("",
                Collections.singletonList("whatsapp"), null, null, null, null));
    }

    @Test
    public void repeatedCategoriesAreOrGrouped() {
        assertEquals("(category:\"whatsapp\" OR category:\"email\")", Search.buildStructuredQuery("",
                Arrays.asList("whatsapp", "email"), null, null, null, null));
    }

    @Test
    public void categoryValuesAreTrimmed() {
        assertEquals("category:\"whatsapp\"", Search.buildStructuredQuery("",
                Collections.singletonList("  whatsapp  "), null, null, null, null));
    }

    @Test
    public void blankCategoryValuesAreIgnoredLeavingQueryAlone() {
        assertEquals("q1", Search.buildStructuredQuery("q1", Arrays.asList("", "   "),
                null, null, null, null));
    }

    // ---- contentType (exact value; '/' inside the value gets escaped) ----

    @Test
    public void contentTypeClauseEscapesSlash() {
        assertEquals("contentType:\"image\\/jpeg\"", Search.buildStructuredQuery("", null,
                Collections.singletonList("image/jpeg"), null, null, null));
    }

    // ---- ext: leading dot optional, repeatable with OR ----

    @Test
    public void extStripsLeadingDot() {
        assertEquals("ext:\"pdf\"", Search.buildStructuredQuery("", null, null,
                Collections.singletonList(".pdf"), null, null));
    }

    @Test
    public void extWithoutDotIsEquivalent() {
        assertEquals("ext:\"pdf\"", Search.buildStructuredQuery("", null, null,
                Collections.singletonList("pdf"), null, null));
    }

    @Test
    public void repeatedExtsAreOrGrouped() {
        assertEquals("(ext:\"pdf\" OR ext:\"doc\")", Search.buildStructuredQuery("", null, null,
                Arrays.asList(".pdf", "doc"), null, null));
    }

    // ---- dateFrom/dateTo: inclusive range on the 'modified' term field ----

    @Test
    public void dateRangeIsInclusiveOnModifiedField() {
        assertEquals("modified:[2024-01-02T00:00:00Z TO 2024-01-03T23:59:59Z]",
                Search.buildStructuredQuery("", null, null, null, "2024-01-02", "2024-01-03"));
    }

    @Test
    public void dateFromOnlyOpensUpperBound() {
        assertEquals("modified:[2024-01-02T00:00:00Z TO *]",
                Search.buildStructuredQuery("", null, null, null, "2024-01-02", null));
    }

    @Test
    public void dateToOnlyOpensLowerBound() {
        assertEquals("modified:[* TO 2024-01-02T23:59:59Z]",
                Search.buildStructuredQuery("", null, null, null, null, "2024-01-02"));
    }

    @Test
    public void sameDateFromAndToYieldsSingleDayRange() {
        assertEquals("modified:[2024-01-02T00:00:00Z TO 2024-01-02T23:59:59Z]",
                Search.buildStructuredQuery("", null, null, null, "2024-01-02", "2024-01-02"));
    }

    @Test
    public void blankDatesAreTreatedAsAbsent() {
        assertEquals("q", Search.buildStructuredQuery("q", null, null, null, "  ", ""));
    }

    @Test
    public void malformedDateFromThrowsIllegalArgumentNamingParam() {
        try {
            Search.buildStructuredQuery("", null, null, null, "02/01/2024", null);
            fail("expected IllegalArgumentException for malformed dateFrom");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("dateFrom"));
            assertTrue(e.getMessage(), e.getMessage().contains("YYYY-MM-DD"));
        }
    }

    @Test
    public void malformedDateToThrowsIllegalArgumentNamingParam() {
        try {
            Search.buildStructuredQuery("", null, null, null, null, "2024-13-01");
            fail("expected IllegalArgumentException for malformed dateTo");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("dateTo"));
        }
    }

    @Test
    public void reversedDateRangeThrowsIllegalArgument() {
        try {
            Search.buildStructuredQuery("", null, null, null, "2024-01-03", "2024-01-02");
            fail("expected IllegalArgumentException for reversed range");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("invalid range"));
        }
    }

    // ---- escaping: filter values never reach the query raw ----

    @Test
    public void doubleQuoteInsideFilterValueIsEscaped() {
        // QueryParserUtil.escape prefixes the quote with a backslash
        assertEquals("category:\"a\\\"b\"", Search.buildStructuredQuery("",
                Collections.singletonList("a\"b"), null, null, null, null));
    }

    @Test
    public void backslashInsideFilterValueIsEscaped() {
        assertEquals("category:\"a\\\\b\"", Search.buildStructuredQuery("",
                Collections.singletonList("a\\b"), null, null, null, null));
    }

    @Test
    public void typographicQuotesAreNormalizedThenEscaped() {
        // QueryBuilder.escape maps curved/other quotes to '"' before escaping
        assertEquals("category:\"a\\\"\\\"\\\"\\\"z\"", Search.buildStructuredQuery("",
                Collections.singletonList("a\u201C\u201D\u201E\uFF02z"), null, null, null, null));
    }

    // ---- full composition order: ( q ) AND category AND contentType AND ext AND date ----

    @Test
    public void allFiltersComposeInDocumentedOrder() {
        assertEquals("( a ) AND category:\"w\" AND contentType:\"t\" AND ext:\"e\""
                + " AND modified:[2020-01-01T00:00:00Z TO *]",
                Search.buildStructuredQuery("a", Collections.singletonList("w"),
                        Collections.singletonList("t"), Collections.singletonList(".e"),
                        "2020-01-01", null));
    }

    @Test
    public void blankQueryIsNotWrappedWhenFiltersExist() {
        assertEquals("category:\"w\"", Search.buildStructuredQuery("   ",
                Collections.singletonList("w"), null, null, null, null));
    }
}
