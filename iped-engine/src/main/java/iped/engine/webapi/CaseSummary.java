package iped.engine.webapi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.SortedDocValues;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.util.BytesRef;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import iped.data.IIPEDSource;
import iped.engine.data.IPEDSource;
import iped.engine.search.QueryBuilder;
import iped.engine.webapi.json.CaseSummaryJSON;
import iped.properties.BasicProps;

/**
 * CaseSummary answers "what is in this case?" in ONE request (design 07e,
 * backlog F-5): per-category item counts ranked by count, totals and the
 * min/max of the modified field. It is intended as the very first call an
 * LLM agent makes against the API.
 *
 * Mechanism (design 07e D1, implementation-time findings):
 *
 * - Preferred M2 (docFreq over the category term dictionary) was REFUTED at
 * implementation: the category field is analyzed with StandardASCIIAnalyzer
 * (AppAnalyzer), so its term dictionary holds WORDS ("other", "files"), not
 * whole category values. docFreq of a word is not a category count.
 *
 * - M1 count-only (design D1/V2) was chosen: IPEDSearcher has no count-only
 * path (search() materializes every id, CB1), but the per-source shared
 * Lucene IndexSearcher (IPEDSource.getSearcher(), the very same searcher
 * /search uses) exposes count(Query), which counts without materializing
 * any id list.
 *
 * - For each taxonomy leaf category the endpoint builds the EXACT same query
 * /search does for the structured filter (Search.addTermClause string,
 * QueryBuilder.getQuery, QueryBuilder.rewriteQuery and the non-tree
 * exclusion of IPEDSearcher.searchAll) and only counts it. Parity with
 * /search counts is therefore by construction, and cost stays proportional
 * to the taxonomy size (51 cheap count queries), not to materialization.
 * R2 (docFreq vs deletes) is moot in this mechanism: count() sees the same
 * live (delete-aware) reader /search uses.
 *
 * The body is index metadata only; it never promises extracted text (P2-6).
 */
@Api(value = "CaseSummary")
@Path("case-summary")
public class CaseSummary {

    /**
     * Default number of categories returned (design 07e D2: 10 rows cover
     * ~92% of the reference case).
     */
    static final int DEFAULT_TOP = 10;

    /**
     * Upper bound accepted for the top parameter: current taxonomy size (51)
     * plus headroom for taxonomy growth (design 07e contract). Greater
     * values are rejected with 400, never silently clamped.
     */
    static final int MAX_TOP = 64;

    /**
     * Synthetic bucket for documents in scope whose category is absent or
     * outside the taxonomy leaves counted (design 07e D7).
     */
    static final String UNCATEGORIZED = "uncategorized";

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Aggregated case portrait in one request: item counts per category (top-N by count, descending), totals and modified-date period",
            notes = "Cost is proportional to the taxonomy size, never to a full item listing. categories omits empty buckets (census rule); residual marks buckets whose name starts with 'other' plus the synthetic uncategorized entry. The body is index metadata only and makes no promise about extracted text availability.")
    @ApiResponses({
        @ApiResponse(code = 400, message = "invalid top parameter (expected 1..64)"),
        @ApiResponse(code = 404, message = "sourceID does not exist")
    })
    public Response summary(
            @ApiParam(value = "Restrict the summary to this source (case). Omit or empty to summarize ALL loaded sources (sources listed lexicographically). 404 if source does not exist.")
            @DefaultValue("")
            @QueryParam("sourceID") String sourceID,
            @ApiParam(value = "Number of top categories (by item count, descending) to return, 1 to 64. Omit or empty for the default 10. 400 if invalid.")
            @DefaultValue("")
            @QueryParam("top") String top) throws Exception {
        // Design 07e D11: 404 (source) is validated BEFORE 400 (top), same
        // order as /search (P0-2/CB2), and both before touching the index.
        List<String> names = new ArrayList<>();
        List<IPEDSource> scope = new ArrayList<>();
        if (sourceID != null && !sourceID.equals("")) {
            // Sources.getSource throws the P2-1 404 WebApplicationException
            // (text/plain "source not found: <id>"), same contract as
            // /search and /docs.
            scope.add((IPEDSource) Sources.getSource(sourceID));
            names.add(sourceID);
        } else {
            // E7: sources snapshot taken at request start (addSource may add
            // more while the request runs; next request sees them).
            TreeMap<String, IPEDSource> byName = new TreeMap<>();
            for (IIPEDSource s : Sources.multiSource.getAtomicSources()) {
                // The canonical id of every loaded source is always in the
                // Sources maps (openCase/addSource populate them); the
                // synthetic fallback below is defensive only.
                String name = Sources.sourceIntToString.get(s.getSourceId());
                if (name == null) {
                    name = "source-" + s.getSourceId();
                }
                byName.put(name, (IPEDSource) s);
            }
            names.addAll(byName.keySet());
            scope.addAll(byName.values());
        }
        int topN;
        try {
            topN = parseTopParam(top);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.TEXT_PLAIN)
                    .entity(e.getMessage()).build();
        }
        List<String> candidates = Sources.multiSource.getLeafCategories();
        if (candidates == null) {
            candidates = Collections.emptyList();
        }
        Map<String, Long> counts = new TreeMap<>();
        long totalItems = 0;
        BytesRef min = null;
        BytesRef max = null;
        for (IPEDSource src : scope) {
            totalItems += countItems(src);
            countCategories(src, candidates, counts);
            BytesRef[] bounds = modifiedBounds(src.getReader());
            if (bounds != null) {
                if (min == null || bounds[0].compareTo(min) < 0) {
                    min = bounds[0];
                }
                if (max == null || bounds[1].compareTo(max) > 0) {
                    max = bounds[1];
                }
            }
        }
        String from = min == null ? null : min.utf8ToString();
        String to = max == null ? null : max.utf8ToString();
        CaseSummaryJSON body = buildBody(names, totalItems, topN, counts, from, to);
        return Response.ok(body).build();
    }

    /**
     * Validates the raw top parameter: omitted/empty gives the explicit
     * default 10; anything else must parse as int in [1..MAX_TOP]. Invalid
     * values throw IllegalArgumentException carrying the exact 400 message
     * (design 07e E5; discipline 07a/07c: malformed parameter is a contract
     * error, never absorbed).
     */
    static int parseTopParam(String top) {
        if (top == null || top.trim().isEmpty()) {
            return DEFAULT_TOP;
        }
        String raw = top.trim();
        int value;
        try {
            value = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(invalidTopMessage(raw));
        }
        if (value < 1 || value > MAX_TOP) {
            throw new IllegalArgumentException(invalidTopMessage(raw));
        }
        return value;
    }

    static String invalidTopMessage(String raw) {
        return "case-summary: invalid 'top' value '" + raw + "' (expected 1.." + MAX_TOP + ")";
    }

    /**
     * Number of real items in one source (design 07e D7, R5-4c fix A): the
     * same canonical base /search uses for an empty query —
     * QueryBuilder.getMatchAllItemsQuery() plus the non-tree exclusion of
     * IPEDSearcher.searchAll (IPEDSearcher.java:148-155) — counted with
     * IndexSearcher.count(Query), the same count-only mechanism already used
     * for every category bucket. NOT maxDoc: index documents include tree
     * nodes and other structural documents, which are not items (R5-4c
     * probe: maxDoc=1.183.262 vs 591.251 real items in the reference case).
     */
    static long countItems(IPEDSource src) throws Exception {
        BooleanQuery.Builder scoped = new BooleanQuery.Builder();
        scoped.add(QueryBuilder.getMatchAllItemsQuery(), Occur.MUST);
        scoped.add(new TermQuery(new Term(BasicProps.TREENODE, "true")), Occur.MUST_NOT);
        return src.getSearcher().count(scoped.build());
    }

    /**
     * Counts every candidate category in one source without materializing
     * ids. The query for category X is the same string the /search
     * structured filter builds (Search.addTermClause:
     * {@code category:"<escaped>"}), parsed by the same QueryBuilder, passed
     * through the same rewriteQuery and wrapped in the same non-tree
     * exclusion IPEDSearcher.searchAll applies, then counted with
     * IndexSearcher.count(Query): no id list, no scoring. Categories with
     * count 0 are omitted structurally (design 07e D3, census rule).
     */
    static void countCategories(IPEDSource src, List<String> candidates,
            Map<String, Long> counts) throws Exception {
        QueryBuilder parser = new QueryBuilder(src);
        QueryBuilder rewriter = new QueryBuilder(src, true);
        IndexSearcher searcher = src.getSearcher();
        for (String cat : candidates) {
            Query q = parser.getQuery(BasicProps.CATEGORY + ":\"" + QueryBuilder.escape(cat) + "\"");
            q = rewriter.rewriteQuery(q);
            // Same non-tree exclusion as IPEDSearcher.searchAll (tree nodes
            // are structural docs, never items).
            BooleanQuery.Builder scoped = new BooleanQuery.Builder();
            scoped.add(q, Occur.MUST);
            scoped.add(new TermQuery(new Term(BasicProps.TREENODE, "true")), Occur.MUST_NOT);
            int c = searcher.count(scoped.build());
            if (c > 0) {
                Long previous = counts.get(cat);
                counts.put(cat, (previous == null ? 0L : previous) + (long) c);
            }
        }
    }

    /**
     * Cheap min/max of the modified field (design 07e D6/V4): per-segment
     * SortedDocValues boundaries (first/last non-empty ordinal — the empty
     * term of documents without a date is skipped, R5-4c fix B);
     * ordinals of a SortedDocValues field are assigned in lexicographic
     * order and the canonical yyyy-MM-dd'T'HH:mm:ss'Z' format is
     * lexicographically sortable, so this is chronological min/max with no
     * item scan. Returns null (period degrades to null, request stays 200)
     * on ANY problem reading the field (E9: never a 500 because of one
     * field).
     */
    static BytesRef[] modifiedBounds(IndexReader reader) {
        BytesRef min = null;
        BytesRef max = null;
        try {
            for (LeafReaderContext leaf : reader.leaves()) {
                SortedDocValues values = DocValues.getSorted(leaf.reader(), BasicProps.MODIFIED);
                int count = values.getValueCount();
                if (count <= 0) {
                    continue;
                }
                BytesRef low = values.lookupOrd(0);
                if (low != null && low.length == 0) {
                    // Ord 0 can be the empty term: documents without a
                    // modified date store "" (R5-4c probe: ord0 = ""). It
                    // sorts first lexicographically and must never surface
                    // as period.from (design 07e D6: valid date or null).
                    // Ords are lexicographic, so ord 1 is the segment's
                    // real minimum; count == 1 means the segment has only
                    // the empty term and contributes nothing.
                    low = count > 1 ? values.lookupOrd(1) : null;
                }
                if (low != null) {
                    // lookupOrd may hand back a reused scratch BytesRef:
                    // in Lucene 9.2 a second lookupOrd overwrites the
                    // result of the first (proved by direct probe), so
                    // low must be detached BEFORE the high lookup below.
                    low = BytesRef.deepCopyOf(low);
                }
                BytesRef high = values.lookupOrd(count - 1);
                if (high != null && high.length == 0) {
                    high = null; // the segment only holds the empty term
                }
                if (low != null && (min == null || low.compareTo(min) < 0)) {
                    min = BytesRef.deepCopyOf(low);
                }
                if (high != null && (max == null || high.compareTo(max) > 0)) {
                    max = BytesRef.deepCopyOf(high);
                }
            }
        } catch (Exception e) {
            return null;
        }
        if (min == null || max == null) {
            return null;
        }
        return new BytesRef[] { min, max };
    }

    /**
     * Pure body assembly, unit-testable without an index: ranks categories
     * by count desc (ties by name asc, design 07e D2/AC8), adds the
     * synthetic uncategorized bucket when totalItems exceeds the category
     * sum (D7), cuts to top-N and makes the cut auditable through
     * omittedCategories/itemsOutsideTop. distinctCategories/omittedCategories
     * account only for real categories (uncategorized is a synthetic row).
     */
    static CaseSummaryJSON buildBody(List<String> sources, long totalItems, int top,
            Map<String, Long> counts, String from, String to) {
        List<CaseSummaryJSON.CategoryCountJSON> entries = new ArrayList<>();
        long sum = 0;
        for (Map.Entry<String, Long> e : counts.entrySet()) {
            // D3: empty buckets are never represented (census rule).
            if (e.getKey() == null || e.getValue() == null || e.getValue() <= 0) {
                continue;
            }
            // D5: residual marks aggregated buckets by label prefix.
            entries.add(new CaseSummaryJSON.CategoryCountJSON(e.getKey(), e.getValue(),
                    e.getKey().startsWith("other")));
            sum += e.getValue();
        }
        int distinctCategories = entries.size();
        // D7: documents in scope without a counted category compete as a
        // de-facto bucket instead of silently disappearing.
        if (totalItems > sum) {
            entries.add(new CaseSummaryJSON.CategoryCountJSON(UNCATEGORIZED, totalItems - sum, true));
        }
        Collections.sort(entries, (a, b) -> {
            int cmp = Long.compare(b.getItems(), a.getItems());
            return cmp != 0 ? cmp : a.getCategory().compareTo(b.getCategory());
        });
        int shown = Math.min(top, entries.size());
        long itemsOutsideTop = 0;
        int omittedCategories = 0;
        for (int i = shown; i < entries.size(); i++) {
            itemsOutsideTop += entries.get(i).getItems();
            if (!UNCATEGORIZED.equals(entries.get(i).getCategory())) {
                omittedCategories++;
            }
        }
        CaseSummaryJSON.PeriodJSON period = (from == null || to == null) ? null
                : new CaseSummaryJSON.PeriodJSON(from, to);
        return new CaseSummaryJSON(new ArrayList<String>(sources), totalItems, top,
                new ArrayList<CaseSummaryJSON.CategoryCountJSON>(entries.subList(0, shown)),
                distinctCategories, omittedCategories, Math.max(0, itemsOutsideTop), period);
    }
}
