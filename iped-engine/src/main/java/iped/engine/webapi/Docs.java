package iped.engine.webapi;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import iped.data.IIPEDSource;
import iped.engine.webapi.json.DocPropsJSON;

@Api(value = "Documents")
@Path("sources/{sourceID}/docs")
public class Docs {

    // Default-deny policy (design 06b): ALLOWED_FIELDS is a curated static
    // allowlist, initially equal to DEFAULT_FIELDS; it grows only by explicit
    // review. Field names confirmed against the real index at runtime:
    // hash, category, type, ext, contentType, path.
    private static final Set<String> DEFAULT_FIELDS = Collections
            .unmodifiableSet(new HashSet<String>(Arrays.asList(
                    "hash", "category", "type", "ext", "contentType", "path")));
    private static final Set<String> ALLOWED_FIELDS = DEFAULT_FIELDS;

    @ApiOperation(value = "Get document's properties")
    @GET
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public static DocPropsJSON properties(@PathParam("sourceID") String sourceID, @PathParam("id") int id)
            throws IOException {
        IIPEDSource source = Sources.getSource(sourceID);
        int luceneID = source.getLuceneId(id);
        Document doc = source.getReader().document(luceneID);

        DocPropsJSON result = new DocPropsJSON();
        result.setSource(sourceID);
        result.setId(id);
        result.setLuceneId(luceneID);
        Map<String, String[]> properties = new HashMap<String, String[]>();
        for (IndexableField field : doc.getFields()) {
            String[] values = doc.getValues(field.name());
            properties.put(field.name(), values);
        }
        result.setProperties(properties);

        result.setBookmarks(source.getBookmarks().getBookmarkList(id));
        result.setSelected(source.getBookmarks().isChecked(id));

        return result;
    }

    @ApiOperation(value = "Get selected properties of a document (allowlisted fields only)")
    @ApiResponses({
        @ApiResponse(code = 400, message = "unknown or not permitted field"),
        @ApiResponse(code = 404, message = "source or document not found") })
    @GET
    @Path("{id}/properties")
    @Produces(MediaType.APPLICATION_JSON)
    public Response selectedProperties(@PathParam("sourceID") String sourceID, @PathParam("id") int id,
            @ApiParam(value = "Comma-separated fields to return. Valid fields: hash, category, type, ext, contentType, path. Omit or leave empty for the default set.")
            @QueryParam("fields") String fields) throws IOException {

        // 1. sourceID existence first (avoids NPE/500 of Sources.getSource,
        //    Sources.java:85-88); same check pattern as Search.java (P0-2).
        if (!Sources.sourceStringToInt.containsKey(sourceID)) {
            return Response.status(Response.Status.NOT_FOUND)
                    .type(MediaType.TEXT_PLAIN)
                    .entity("source not found: " + sourceID).build();
        }

        // 2. fields syntax/allowlist before any Lucene I/O (fail-fast).
        Set<String> requested = parseFields(fields);
        for (String field : requested) {
            if (!ALLOWED_FIELDS.contains(field)) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .type(MediaType.TEXT_PLAIN)
                        .entity("unknown or not permitted field: " + field
                                + " (valid: " + ALLOWED_FIELDS + ")").build();
            }
        }

        // 3. document lookup; out-of-range id -> 404 (docs[id] throws
        //    ArrayIndexOutOfBoundsException, a subclass of
        //    IndexOutOfBoundsException - IPEDSource.java:777-779).
        IIPEDSource source = Sources.getSource(sourceID);
        int luceneID;
        Document doc;
        try {
            luceneID = source.getLuceneId(id);
            doc = source.getReader().document(luceneID);
        } catch (IndexOutOfBoundsException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .type(MediaType.TEXT_PLAIN)
                    .entity("document not found: " + id).build();
        }

        // 4. reuse existing DTO; only 'properties' is filtered. Allowed but
        //    absent fields are omitted (200).
        Map<String, String[]> properties = new HashMap<String, String[]>();
        for (String field : requested) {
            String[] values = doc.getValues(field);
            if (values != null && values.length > 0) {
                properties.put(field, values);
            }
        }

        DocPropsJSON result = new DocPropsJSON();
        result.setSource(sourceID);
        result.setId(id);
        result.setLuceneId(luceneID);
        result.setProperties(properties);
        result.setBookmarks(source.getBookmarks().getBookmarkList(id));
        result.setSelected(source.getBookmarks().isChecked(id));
        return Response.ok(result).build();
    }

    // null/empty/comma-only -> DEFAULT_FIELDS; trims tokens, drops empties,
    // dedupes (LinkedHashSet keeps the 400 message order deterministic).
    private static Set<String> parseFields(String fields) {
        Set<String> parsed = new LinkedHashSet<String>();
        if (fields != null) {
            for (String token : fields.split(",")) {
                String trimmed = token.trim();
                if (!trimmed.isEmpty()) {
                    parsed.add(trimmed);
                }
            }
        }
        return parsed.isEmpty() ? DEFAULT_FIELDS : parsed;
    }
}
