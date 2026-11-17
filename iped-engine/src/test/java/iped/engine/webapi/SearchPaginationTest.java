package iped.engine.webapi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.junit.Test;

import iped.engine.webapi.json.DocIDJSON;
import iped.engine.webapi.json.SourceToIDsJSON;
import iped.engine.webapi.json.SourceToIDsPageJSON;

/**
 * Unit tests for the /search pagination contract (design 07a, feature F-1)
 * without a server or an index:
 *
 * <ul>
 * <li>{@link Search#parsePaginationParam} parsing rules (package-private and
 * called directly): null/blank means absent, values are trimmed, a
 * non-integer raises the IllegalArgumentException that doSearch maps to
 * HTTP 400 naming the offending parameter.</li>
 * <li>Range validation (limit 1..10000, offset &gt;= 0) lives inside
 * {@link Search#doSearch()} itself, but it runs BEFORE any index access, so
 * the 400 responses for limit=0, negative/out-of-range values and malformed
 * numbers are exercised here by instantiating the JAX-RS resource directly
 * and asserting the built Response. NOTE: the sourceID field is intentionally
 * never set in these tests - with no sources loaded, Sources.sourceStringToInt
 * is null and the sourceID 404 check (Search.java:89) would NPE; that ordering
 * is covered end-to-end by the HTTP harness instead.</li>
 * <li>{@link Search#paginate} window semantics (no reordering, empty window
 * past the end, partial page, null limit runs to the end) and preservation of
 * {@code total} in {@link SourceToIDsPageJSON} regardless of the window.</li>
 * </ul>
 */
public class SearchPaginationTest {

    // ---- parsePaginationParam: parsing contract ----

    @Test
    public void absentParametersParseToNull() {
        assertNull(Search.parsePaginationParam("limit", null));
        assertNull(Search.parsePaginationParam("limit", ""));
        assertNull(Search.parsePaginationParam("offset", "   "));
    }

    @Test
    public void valuesAreTrimmedAndParsed() {
        assertEquals(Integer.valueOf(5), Search.parsePaginationParam("limit", "5"));
        assertEquals(Integer.valueOf(7), Search.parsePaginationParam("limit", " 7 "));
        assertEquals(Integer.valueOf(0), Search.parsePaginationParam("offset", "0"));
        // parsePaginationParam only parses; the 1..MAX_LIMIT range check is
        // applied by the caller (doSearch) and asserted further below.
        assertEquals(Integer.valueOf(-3), Search.parsePaginationParam("offset", "-3"));
    }

    @Test
    public void malformedValueThrowsNamingTheParameter() {
        try {
            Search.parsePaginationParam("limit", "abc");
            fail("expected IllegalArgumentException for 'abc'");
        } catch (IllegalArgumentException e) {
            assertEquals("invalid limit value 'abc' (expected integer)", e.getMessage());
        }
        try {
            Search.parsePaginationParam("offset", "1.5");
            fail("expected IllegalArgumentException for '1.5'");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage(), e.getMessage().startsWith("invalid offset value '1.5'"));
        }
    }

    // ---- doSearch: 400 contract (reached before any index access) ----

    private static void assertBadRequest(Search search, String expectedEntity) throws Exception {
        Response response = search.doSearch();
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertEquals(MediaType.TEXT_PLAIN_TYPE, response.getMediaType());
        assertEquals(expectedEntity, response.getEntity());
    }

    @Test
    public void zeroLimitIsRejected400() throws Exception {
        Search search = new Search();
        search.limit = "0";
        assertBadRequest(search, "invalid limit value '0' (expected 1..10000)");
    }

    @Test
    public void negativeLimitIsRejected400() throws Exception {
        Search search = new Search();
        search.limit = "-1";
        assertBadRequest(search, "invalid limit value '-1' (expected 1..10000)");
    }

    @Test
    public void limitAboveMaxIsRejected400NotClamped() throws Exception {
        Search search = new Search();
        search.limit = "99999";
        assertBadRequest(search, "invalid limit value '99999' (expected 1..10000)");
        assertEquals(10000, Search.MAX_LIMIT);
    }

    @Test
    public void negativeOffsetIsRejected400() throws Exception {
        Search search = new Search();
        search.offset = "-1";
        assertBadRequest(search, "invalid offset value '-1' (expected 0 or greater)");
    }

    @Test
    public void malformedLimitIsRejected400NamingParam() throws Exception {
        Search search = new Search();
        search.limit = "abc";
        assertBadRequest(search, "invalid limit value 'abc' (expected integer)");
    }

    @Test
    public void malformedOffsetIsRejected400NamingParam() throws Exception {
        Search search = new Search();
        search.offset = "x";
        assertBadRequest(search, "invalid offset value 'x' (expected integer)");
    }

    @Test
    public void filterValidationPrecedesPaginationValidation() throws Exception {
        // Documented order (Search.java): 404 source -> 400 filter -> 400
        // pagination. With both a bad dateFrom and a bad limit, the date
        // message must win. (The sourceID-404-first rule cannot be unit-tested
        // without a loaded source and is covered by the HTTP harness.)
        Search search = new Search();
        search.dateFrom = "02/01/2024";
        search.limit = "0";
        assertBadRequest(search, "invalid dateFrom value '02/01/2024' (expected YYYY-MM-DD)");
    }

    // ---- paginate: window semantics without reordering ----

    private static List<DocIDJSON> docs(String source, int count) {
        List<DocIDJSON> list = new ArrayList<DocIDJSON>();
        for (int i = 0; i < count; i++) {
            list.add(new DocIDJSON(source, i));
        }
        return list;
    }

    @Test
    public void nullLimitRunsToTheEnd() {
        List<DocIDJSON> all = docs("A", 5);
        List<DocIDJSON> window = Search.paginate(all, 0, null);
        assertEquals(5, window.size());
        for (int i = 0; i < 5; i++) {
            assertSame(all.get(i), window.get(i)); // same order, same elements
        }
    }

    @Test
    public void partialPageWhenOffsetPlusLimitPassesTheEnd() {
        List<DocIDJSON> window = Search.paginate(docs("A", 5), 3, 10);
        assertEquals(2, window.size());
        assertEquals(3, window.get(0).getId());
        assertEquals(4, window.get(1).getId());
    }

    @Test
    public void offsetBeyondTheEndYieldsEmptyWindow() {
        assertEquals(0, Search.paginate(docs("A", 5), 10, 2).size());
        assertEquals(0, Search.paginate(docs("A", 5), 5, 2).size()); // offset == total
    }

    @Test
    public void middleWindowKeepsIterationOrder() {
        List<DocIDJSON> window = Search.paginate(docs("A", 5), 1, 2);
        assertEquals(2, window.size());
        assertEquals(1, window.get(0).getId());
        assertEquals(2, window.get(1).getId());
    }

    // ---- SourceToIDsPageJSON: total preserved regardless of the window ----

    @Test
    public void pageKeepsTotalIndependentlyOfTheReturnedWindow() {
        List<DocIDJSON> all = docs("A", 5);
        List<DocIDJSON> window = Search.paginate(all, 4, 2);
        assertEquals(1, window.size());
        SourceToIDsPageJSON page = new SourceToIDsPageJSON(window, all.size());
        assertEquals(5, page.getTotal());
        assertEquals(1, page.getData().get(0).getIds().size());
        assertEquals(Integer.valueOf(4), page.getData().get(0).getIds().get(0));
    }

    @Test
    public void emptyWindowStillReportsTotal() {
        SourceToIDsPageJSON page = new SourceToIDsPageJSON(Search.paginate(docs("A", 5), 9, 2), 5);
        assertEquals(5, page.getTotal());
        assertTrue(page.getData().isEmpty());
    }

    @Test
    public void pageIsCompatibleWithTheLegacyDto() {
        // The paginated variant extends the legacy DTO, so the "data" shape is
        // unchanged and only "total" is added (design 07a compatibility).
        SourceToIDsPageJSON page = new SourceToIDsPageJSON(docs("A", 2), 2);
        assertTrue(page instanceof SourceToIDsJSON);
        assertEquals(2, page.getTotal());
    }
}
