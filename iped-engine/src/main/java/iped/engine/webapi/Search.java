package iped.engine.webapi;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

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
import iped.data.IItemId;
import iped.engine.data.IPEDSource;
import iped.engine.search.IPEDSearcher;
import iped.engine.search.QueryBuilder;
import iped.engine.webapi.json.DocIDJSON;
import iped.engine.webapi.json.SourceToIDsJSON;
import iped.engine.webapi.json.SourceToIDsPageJSON;
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
    @ApiParam(value = "Filter by file extension; leading dot optional (pdf and .pdf are equivalent). Repeatable; multiple values match with OR.", allowMultiple = true)
    @QueryParam("ext")
    List<String> ext;
    @ApiParam(value = "Only items modified on or after this date (inclusive), format YYYY-MM-DD. 400 if invalid.")
    @QueryParam("dateFrom")
    String dateFrom;
    @ApiParam(value = "Only items modified on or before this date (inclusive), format YYYY-MM-DD. 400 if invalid or before dateFrom.")
    @QueryParam("dateTo")
    String dateTo;
    @ApiParam(value = "Maximum number of ids to return, 1 to 10000. Recommended page size: 100, max 10000. Omit or empty to return all ids (legacy response, no total). 400 if invalid.")
    @QueryParam("limit")
    String limit;
    @ApiParam(value = "Number of ids to skip, 0 or greater. Omit or empty means 0. Sending limit and/or offset also adds a 'total' field to the response. 400 if invalid.")
    @QueryParam("offset")
    String offset;

    /**
     * Field used by the dateFrom/dateTo filters. Verified against a real index
     * (design 06c TO_VERIFY): stored as a non-tokenized term in the canonical
     * format yyyy-MM-dd'T'HH:mm:ss'Z' (UTC) by iped.engine.task.index.IndexItem
     * via iped.utils.DateUtil.dateToString.
     */
    static final String DATE_FIELD = "modified";

    /**
     * Upper bound accepted for the limit parameter (design 07a): greater
     * values are rejected with 400, never silently clamped.
     */
    static final int MAX_LIMIT = 10000;

    @ApiOperation(value = "Search documents")
    @ApiResponses({
        @ApiResponse(code = 400,
                message = "invalid structured filter value (dateFrom/dateTo) or pagination parameter (limit/offset)"),
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
            effectiveQ = buildStructuredQuery(q, category, contentType, ext, dateFrom, dateTo);
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
        return Response.ok(new SourceToIDsPageJSON(paginate(docs, itemsOffset, pageSize), total)).build();
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
     * Builds the effective Lucene query from q plus the structured filters
     * (design 06c). Filter values never reach the query raw: text fields are
     * escaped and quoted via QueryBuilder.escape; dates are validated with
     * LocalDate.parse and the range clause is assembled exclusively from the
     * parsed values. With no filter clause present the returned string is
     * byte-identical to the legacy behaviour (q with the slash replacement
     * applied and nothing else).
     *
     * @throws IllegalArgumentException with a client friendly message when a
     *                                  filter value is malformed (mapped to HTTP 400).
     */
    static String buildStructuredQuery(String q, List<String> category, List<String> contentType,
            List<String> ext, String dateFrom, String dateTo) {
        if (q == null) {
            q = "";
        }
        List<String> clauses = new ArrayList<String>();
        addTermClause(clauses, "category", category, false);
        addTermClause(clauses, "contentType", contentType, false);
        addTermClause(clauses, "ext", ext, true);
        String dateClause = buildDateClause(dateFrom, dateTo);
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

    private static String buildDateClause(String dateFrom, String dateTo) {
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
        return DATE_FIELD + ":[" + lower + " TO " + upper + "]";
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
