package iped.engine.webapi;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import iped.data.IIPEDSource;
import iped.data.IItem;
import iped.data.IItemId;
import iped.engine.data.IPEDSource;
import iped.engine.search.IPEDSearcher;
import iped.engine.search.QueryBuilder;
import iped.engine.webapi.json.DocIDJSON;
import iped.engine.webapi.json.SourceToIDsJSON;
import iped.engine.webapi.json.SourceToIDsPageJSON;
import iped.engine.webapi.json.SourceToIDsSnippetsJSON;
import iped.search.IIPEDSearcher;
import iped.search.IMultiSearchResult;
import iped.search.SearchResult;

@Api(value = "Search")
@Path("search")
public class Search {

    @DefaultValue("")
    @QueryParam("q")
    String q;
    @DefaultValue("")
    @ApiParam(value = "Restrict search to this source (case). Omit or empty to search ALL loaded sources. 404 if source does not exist.")
    @QueryParam("sourceID")
    String sourceID;

    @ApiParam(value = "Filter by item category (exact value). Repeatable; multiple values match with OR.", allowMultiple = true)
    @QueryParam("category")
    List<String> category;
    @ApiParam(value = "Filter by MIME content type (exact value). Repeatable; multiple values match with OR.", allowMultiple = true)
    @QueryParam("contentType")
    List<String> contentType;
    @ApiParam(value = "Filter by item type as stored in the search index (exact value, e.g. jpg, file, hot). Leading dot optional. Repeatable; multiple values match with OR.", allowMultiple = true)
    @QueryParam("type")
    List<String> type;
    @DefaultValue("modified")
    @ApiParam(value = "Date field searched by the dateFrom/dateTo filters. One of: modified, accessed, created. 400 if invalid.", allowableValues = "modified,accessed,created")
    @QueryParam("dateField")
    String dateField;
    @ApiParam(value = "Only items with dateField on or after this date (inclusive), format YYYY-MM-DD. 400 if invalid.")
    @QueryParam("dateFrom")
    String dateFrom;
    @ApiParam(value = "Only items with dateField on or before this date (inclusive), format YYYY-MM-DD. 400 if invalid or before dateFrom.")
    @QueryParam("dateTo")
    String dateTo;
    @ApiParam(value = "Maximum number of ids to return, 1 to 10000. Recommended page size: 100, max 10000. Omit or empty to return all ids (legacy response, no total). 400 if invalid.")
    @QueryParam("limit")
    String limit;
    @ApiParam(value = "Number of ids to skip, 0 or greater. Omit or empty means 0. Sending limit and/or offset also adds a 'total' field to the response. 400 if invalid.")
    @QueryParam("offset")
    String offset;
    @ApiParam(value = "Return a plain-text snippet of up to N characters per id in an extra 'snippets' map keyed by id (string). Requires limit between 1 and 100 because each snippet costs one on-demand text extraction. Best-effort: ids without extractable text, with failed extraction or after a 10s total budget are simply omitted from the map. Omit, empty or 0 disables snippets (byte-identical response). 400 if invalid.")
    @QueryParam("snippet")
    String snippet;

    /**
     * Default field used by the dateFrom/dateTo filters. Verified against a
     * real index (design 06c TO_VERIFY): stored as a non-tokenized term in the
     * canonical format yyyy-MM-dd'T'HH:mm:ss'Z' (UTC) by
     * iped.engine.task.index.IndexItem via iped.utils.DateUtil.dateToString.
     */
    static final String DATE_FIELD = "modified";
    /**
     * Allowlist accepted by the dateField query parameter (PR #2961 feedback:
     * "making it possible to specify the date field to search into"). All
     * three fields were verified searchable on a real index through range
     * probes with non-zero totals (modified=191423, accessed=7207 and
     * created=53437 documents after 2020-01-01, R5-2 live probes); any other
     * value is rejected with 400.
     */
    static final List<String> DATE_FIELDS = Arrays.asList(DATE_FIELD, "accessed", "created");

    /**
     * Upper bound accepted for the limit parameter (design 07a): greater
     * values are rejected with 400, never silently clamped.
     */
    static final int MAX_LIMIT = 10000;

    /**
     * Upper bound accepted for the snippet parameter (design 07c): greater
     * values are rejected with 400, never silently clamped.
     */
    static final int MAX_SNIPPET_CHARS = 500;

    /**
     * Maximum page size accepted while snippets are enabled (design 07c):
     * every snippet costs one on-demand text extraction in this process
     * (P2-6), so a snippet value greater than zero requires limit in
     * [1..100].
     */
    static final int SNIPPET_MAX_LIMIT = 100;

    /**
     * Per-item text read budget while building snippets (design 07c
     * SCAN_CAP_BYTES): text is streamed through the same acquisition path as
     * /text and reading stops after this many characters, so huge documents
     * are never materialized in memory.
     */
    static final int SCAN_CAP_BYTES = 1024 * 1024;

    /**
     * Global best-effort time budget for all snippet extractions of a single
     * request (design 07c): ids after the deadline are simply omitted from
     * the snippets map (documented partial-map behavior).
     */
    static final long SNIPPET_TIME_BUDGET_MS = 10000L;

    private static final Logger log = Logger.getLogger(Search.class.getName());

    @ApiOperation(value = "Search documents")
    @ApiResponses({
        @ApiResponse(code = 400,
                message = "invalid structured filter value (dateField/dateFrom/dateTo), pagination parameter (limit/offset) or snippet parameter (snippet requires limit 1..100)"),
        @ApiResponse(code = 404, message = "sourceID does not exist")
    })
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response doSearch() throws Exception {
        if (sourceID != null && !sourceID.equals("") && !Sources.sourceStringToInt.containsKey(sourceID)) {
            return Response.status(Response.Status.NOT_FOUND)
                    .type(MediaType.TEXT_PLAIN)
                    .entity("source not found: " + sourceID).build();
        }
        String effectiveQ;
        try {
            effectiveQ = buildStructuredQuery(q, category, contentType, type, dateField, dateFrom, dateTo);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.TEXT_PLAIN)
                    .entity(e.getMessage()).build();
        }
        // Pagination (design 07a): parsed after the sourceID 404 check and
        // after the F-2 filter 400, before the search runs. Malformed or
        // out-of-range values answer 400 naming the offending parameter
        // (manual parse, same discipline as dateFrom/dateTo: an empty value
        // is treated as absent).
        Integer pageSize;
        int itemsOffset;
        boolean paginated;
        try {
            pageSize = parsePaginationParam("limit", limit);
            if (pageSize != null && (pageSize <= 0 || pageSize > MAX_LIMIT)) {
                throw new IllegalArgumentException("invalid limit value '" + limit.trim()
                        + "' (expected 1.." + MAX_LIMIT + ")");
            }
            Integer offsetValue = parsePaginationParam("offset", offset);
            itemsOffset = offsetValue == null ? 0 : offsetValue.intValue();
            if (itemsOffset < 0) {
                throw new IllegalArgumentException(
                        "invalid offset value '" + offset.trim() + "' (expected 0 or greater)");
            }
            paginated = pageSize != null || offsetValue != null;
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.TEXT_PLAIN)
                    .entity(e.getMessage()).build();
        }
        // Snippets (design 07c): validated last, keeping the established
        // precedence sourceID 404 -> F-2 filter 400 -> pagination 400 ->
        // snippet 400 (an earlier invalid parameter always wins). snippet>0
        // requires an explicit window of at most SNIPPET_MAX_LIMIT ids
        // because each snippet costs one text extraction (P2-6).
        int snippetMax;
        try {
            snippetMax = resolveSnippet(snippet);
            if (snippetMax > 0 && (pageSize == null || pageSize > SNIPPET_MAX_LIMIT)) {
                throw new IllegalArgumentException(
                        "snippet requires limit between 1 and " + SNIPPET_MAX_LIMIT);
            }
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .type(MediaType.TEXT_PLAIN)
                    .entity(e.getMessage()).build();
        }
        List<DocIDJSON> docs = new ArrayList<DocIDJSON>();
        if (sourceID == null || sourceID.equals("")) {
            IPEDSearcher searcher = new IPEDSearcher(Sources.multiSource, effectiveQ);
            IMultiSearchResult result = searcher.multiSearch();
            for (IItemId id : result.getIterator()) {
                docs.add(new DocIDJSON(Sources.sourceIntToString.get(id.getSourceId()), id.getId()));
            }
        } else {
            IPEDSource source = (IPEDSource) Sources.getSource(sourceID);
            IIPEDSearcher searcher = new IPEDSearcher(source, effectiveQ);
            SearchResult result = searcher.search();
            for (int id : result.getIds()) {
                docs.add(new DocIDJSON(sourceID, id));
            }
        }

        if (!paginated) {
            // Legacy contract (design 07a): without an explicit limit/offset
            // the response stays byte-identical (same DTO, no total field).
            return Response.ok(new SourceToIDsJSON(docs)).build();
        }
        int total = docs.size();
        List<DocIDJSON> page = paginate(docs, itemsOffset, pageSize);
        if (snippetMax > 0) {
            // Design 07c: ids and total are identical to the same paginated
            // request without snippet; each group of the window just gains a
            // best-effort "snippets" map (id as string -> text excerpt).
            return Response.ok(new SourceToIDsSnippetsJSON(page, total,
                    buildSnippets(page, q, snippetMax))).build();
        }
        return Response.ok(new SourceToIDsPageJSON(page, total)).build();
    }

    /**
     * Parses an optional pagination query parameter (design 07a). Null or
     * blank means absent; a non-integer value throws with a client friendly
     * message naming the parameter (mapped to HTTP 400).
     *
     * @throws IllegalArgumentException when the value is not an integer.
     */
    static Integer parsePaginationParam(String param, String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "invalid " + param + " value '" + value.trim() + "' (expected integer)");
        }
    }

    /**
     * Applies the pagination window to the already materialized result
     * sequence WITHOUT reordering it (design 07a): an offset beyond the end
     * yields an empty window; offset+limit beyond the end yields a partial
     * page; a null limit runs to the end.
     */
    static List<DocIDJSON> paginate(List<DocIDJSON> docs, int offset, Integer limit) {
        int total = docs.size();
        int from = Math.min(offset, total);
        int to = limit == null ? total : Math.min(from + limit.intValue(), total);
        return docs.subList(from, to);
    }

    /**
     * Parses the optional snippet query parameter (design 07c). Null, blank
     * or 0 disables snippets (byte-identical legacy response); the accepted
     * range is 0..{@link #MAX_SNIPPET_CHARS} characters per id. Malformed or
     * out-of-range values throw with a client friendly message naming the
     * parameter (mapped to HTTP 400), never silently clamped.
     *
     * @throws IllegalArgumentException when the value is not a valid snippet
     *                                  value.
     */
    static int resolveSnippet(String snippet) {
        if (snippet == null || snippet.trim().isEmpty()) {
            return 0;
        }
        int value;
        try {
            value = Integer.parseInt(snippet.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "invalid snippet value '" + snippet.trim() + "' (expected integer)");
        }
        if (value < 0 || value > MAX_SNIPPET_CHARS) {
            throw new IllegalArgumentException("invalid snippet value '" + snippet.trim()
                    + "' (expected 0.." + MAX_SNIPPET_CHARS + ")");
        }
        return value;
    }

    /**
     * Builds the per-source snippets map for one paginated window
     * (design 07c): source -&gt; (id as string -&gt; snippet text), in page
     * order. Every group of the window is present, possibly with an empty
     * map; items without text, with failed extraction or placed after the
     * {@link #SNIPPET_TIME_BUDGET_MS} deadline is exceeded are simply
     * omitted. A snippet failure never turns /search into a 500 (design 07c
     * edge cases 4/5/6).
     */
    private Map<String, Map<String, String>> buildSnippets(List<DocIDJSON> page, String q, int max) {
        Map<String, Map<String, String>> bySource = new LinkedHashMap<String, Map<String, String>>();
        for (DocIDJSON doc : page) {
            if (!bySource.containsKey(doc.getSource())) {
                bySource.put(doc.getSource(), new LinkedHashMap<String, String>());
            }
        }
        Map<String, IIPEDSource> resolved = new HashMap<String, IIPEDSource>();
        long deadline = System.currentTimeMillis() + SNIPPET_TIME_BUDGET_MS;
        for (DocIDJSON doc : page) {
            if (System.currentTimeMillis() > deadline) {
                break;
            }
            try {
                IIPEDSource source = resolved.get(doc.getSource());
                if (source == null) {
                    source = Sources.getSource(doc.getSource());
                    resolved.put(doc.getSource(), source);
                }
                IItem item = source.getItemByID(doc.getId());
                String excerpt = makeSnippet(item, source, q, max);
                if (excerpt != null) {
                    bySource.get(doc.getSource()).put(String.valueOf(doc.getId()), excerpt);
                }
            } catch (Throwable t) {
                // Defensive: even item/source resolution failures only omit
                // the key (the inner makeSnippet catch already covers text
                // extraction itself).
                log.log(Level.INFO, "snippet omitted for source " + doc.getSource()
                        + " id " + doc.getId(), t);
            }
        }
        return bySource;
    }

    /**
     * Extracts one item snippet through the same text acquisition path as
     * /text (design 07c, P0-1/R4-2), reading at most
     * {@link #SCAN_CAP_BYTES} characters. Any Throwable is logged and
     * answered with null: the id is then simply absent from the snippets
     * map, never a 500.
     */
    static String makeSnippet(IItem item, IIPEDSource source, String q, int max) {
        try {
            String text = Text.readPlainText(item, source, SCAN_CAP_BYTES);
            return snippetFromText(text, q, max);
        } catch (Throwable t) {
            log.log(Level.INFO, "snippet omitted for item (text extraction failed)", t);
            return null;
        }
    }

    /**
     * Pure slicing function of design 07c (unit tested without a server):
     * returns a plain-text snippet of at most max characters ending at the
     * first occurrence (case-insensitive) of any candidate term extracted
     * from the raw q, flush with the text start when the match is early.
     * When no term occurs in the text (or q has no usable term, e.g. only
     * wildcards/operators) a head fragment is returned. Whitespace runs are
     * collapsed to single spaces, words cut on the borders are trimmed and
     * the ellipsis marks (a leading "\u2026 " and a trailing "\u2026 ")
     * flag the cuts without counting towards max: the returned length is at
     * most max + 4. Returns null for null/empty/blank text so the caller
     * omits the id from the snippets map (design 07c edge case 4, "never an
     * empty value").
     */
    static String snippetFromText(String text, String q, int max) {
        if (text == null) {
            return null;
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) {
            return null;
        }
        int len = normalized.length();
        String lower = normalized.toLowerCase(Locale.ROOT);
        int pos = -1;
        int termLength = 0;
        for (String term : snippetTerms(q)) {
            int at = lower.indexOf(term);
            if (at >= 0 && (pos < 0 || at < pos)) {
                pos = at;
                termLength = term.length();
            }
        }
        int start;
        int end;
        if (pos < 0) {
            // Head fragment (design 07c steps 4/5): q without usable terms
            // or no term occurs within the scanned text.
            start = 0;
            end = trimSnippetEnd(normalized, Math.min(max, len), 0);
        } else {
            // Window of max characters ending at the match, flush with the
            // text start when the match is early (design 07c step 6).
            end = pos + termLength;
            start = trimSnippetStart(normalized, Math.max(0, end - max), pos);
            end = trimSnippetEnd(normalized, end, pos + termLength);
        }
        StringBuilder snippet = new StringBuilder();
        if (start > 0) {
            snippet.append("\u2026 ");
        }
        snippet.append(normalized, start, end);
        if (end < len) {
            snippet.append(" \u2026");
        }
        return snippet.toString();
    }

    /**
     * Drops a word cut on the left border of the window (design 07c step 6)
     * and a leading space left by a previous cut, but only when doing so
     * cannot reach the matched term: the result never passes matchPos.
     */
    private static int trimSnippetStart(String text, int start, int matchPos) {
        if (start <= 0) {
            return 0;
        }
        if (text.charAt(start) == ' ') {
            return start + 1;
        }
        if (text.charAt(start - 1) == ' ') {
            return start;
        }
        int space = text.indexOf(' ', start);
        return (space >= 0 && space < matchPos) ? space + 1 : start;
    }

    /**
     * Drops a word cut on the right border of the window (design 07c step 6)
     * and a trailing space, never cutting into the matched term: the
     * [matchMin, end) region is always kept. No-op at the text end, where no
     * trailing ellipsis is added.
     */
    private static int trimSnippetEnd(String text, int end, int matchMin) {
        if (end >= text.length()) {
            return end;
        }
        if (text.charAt(end) == ' ') {
            return end;
        }
        int space = text.lastIndexOf(' ', end - 1);
        return space >= matchMin ? space : end;
    }

    /**
     * Extracts the candidate centering terms from the RAW q (design 07c
     * step 4): whitespace-split tokens, Lucene boolean/range operators
     * dropped, field prefixes (field:value) removed, wildcards and operator
     * characters stripped, literals shorter than 2 characters discarded and
     * the rest lowercased for case-insensitive matching. An empty list
     * (q empty, "*" or operators only) means "head fragment". Filter values
     * never enter: this runs on q, not on effectiveQ.
     */
    static List<String> snippetTerms(String q) {
        List<String> terms = new ArrayList<String>();
        if (q == null) {
            return terms;
        }
        for (String token : q.split("\\s+")) {
            if (token.equalsIgnoreCase("AND") || token.equalsIgnoreCase("OR")
                    || token.equalsIgnoreCase("NOT") || token.equalsIgnoreCase("TO")) {
                continue;
            }
            int colon = token.indexOf(':');
            String value = colon >= 0 ? token.substring(colon + 1) : token;
            StringBuilder cleaned = new StringBuilder();
            for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                if ("+-!(){}[]^\"~*?\\/&|".indexOf(c) < 0) {
                    cleaned.append(c);
                }
            }
            value = cleaned.toString().toLowerCase(Locale.ROOT);
            if (value.length() >= 2 && !terms.contains(value)) {
                terms.add(value);
            }
        }
        return terms;
    }

    /**
     * Builds the effective Lucene query from q plus the structured filters
     * (design 06c). Filter values never reach the query raw: text fields are
     * escaped and quoted via QueryBuilder.escape; dateField is validated
     * against the {@link #DATE_FIELDS} allowlist; dates are validated with
     * LocalDate.parse and the range clause is assembled exclusively from the
     * parsed values. With no filter clause present the returned string is
     * byte-identical to the legacy behaviour (q with the slash replacement
     * applied and nothing else).
     *
     * @throws IllegalArgumentException with a client friendly message when a
     *                                  filter value is malformed (mapped to HTTP 400).
     */
    static String buildStructuredQuery(String q, List<String> category, List<String> contentType,
            List<String> type, String dateField, String dateFrom, String dateTo) {
        if (q == null) {
            q = "";
        }
        List<String> clauses = new ArrayList<String>();
        addTermClause(clauses, "category", category, false);
        addTermClause(clauses, "contentType", contentType, false);
        addTermClause(clauses, "type", type, true);
        String dateClause = buildDateClause(resolveDateField(dateField), dateFrom, dateTo);
        if (dateClause != null) {
            clauses.add(dateClause);
        }
        String escapedQ = q.replaceAll("/", "\\\\/");
        if (clauses.isEmpty()) {
            return escapedQ;
        }
        if (!q.trim().isEmpty()) {
            clauses.add(0, "( " + escapedQ + " )");
        }
        return String.join(" AND ", clauses);
    }

    private static void addTermClause(List<String> clauses, String field, List<String> values,
            boolean stripLeadingDot) {
        if (values == null) {
            return;
        }
        List<String> terms = new ArrayList<String>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            value = value.trim();
            if (stripLeadingDot && value.startsWith(".")) {
                value = value.substring(1).trim();
            }
            if (value.isEmpty()) {
                continue;
            }
            terms.add(field + ":\"" + QueryBuilder.escape(value) + "\"");
        }
        if (terms.isEmpty()) {
            return;
        }
        clauses.add(terms.size() == 1 ? terms.get(0) : "(" + String.join(" OR ", terms) + ")");
    }

    private static String buildDateClause(String dateField, String dateFrom, String dateTo) {
        boolean hasFrom = dateFrom != null && !dateFrom.trim().isEmpty();
        boolean hasTo = dateTo != null && !dateTo.trim().isEmpty();
        if (!hasFrom && !hasTo) {
            return null;
        }
        LocalDate from = null;
        LocalDate to = null;
        if (hasFrom) {
            from = parseISODate("dateFrom", dateFrom.trim());
        }
        if (hasTo) {
            to = parseISODate("dateTo", dateTo.trim());
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "invalid range: dateFrom '" + from + "' is after dateTo '" + to + "'");
        }
        String lower = from == null ? "*" : from + "T00:00:00Z";
        String upper = to == null ? "*" : to + "T23:59:59Z";
        return dateField + ":[" + lower + " TO " + upper + "]";
    }

    /**
     * Validates the optional dateField query parameter against
     * {@link #DATE_FIELDS}. Null or blank means the default ({@link
     * #DATE_FIELD}); any other value throws with a client friendly message
     * naming the parameter (mapped to HTTP 400). Validated even when no date
     * range is requested, so a wrong value is never silently ignored.
     */
    static String resolveDateField(String dateField) {
        if (dateField == null || dateField.trim().isEmpty()) {
            return DATE_FIELD;
        }
        String value = dateField.trim();
        if (!DATE_FIELDS.contains(value)) {
            throw new IllegalArgumentException("invalid dateField value '" + value
                    + "' (expected one of: " + String.join(", ", DATE_FIELDS) + ")");
        }
        return value;
    }
    private static LocalDate parseISODate(String param, String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "invalid " + param + " value '" + value + "' (expected YYYY-MM-DD)");
        }
    }
}
