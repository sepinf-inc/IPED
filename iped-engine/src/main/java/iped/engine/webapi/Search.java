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

    /**
     * Field used by the dateFrom/dateTo filters. Verified against a real index
     * (design 06c TO_VERIFY): stored as a non-tokenized term in the canonical
     * format yyyy-MM-dd'T'HH:mm:ss'Z' (UTC) by iped.engine.task.index.IndexItem
     * via iped.utils.DateUtil.dateToString.
     */
    static final String DATE_FIELD = "modified";

    @ApiOperation(value = "Search documents")
    @ApiResponses({
        @ApiResponse(code = 400, message = "invalid structured filter value (dateFrom/dateTo)"),
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

        return Response.ok(new SourceToIDsJSON(docs)).build();
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
