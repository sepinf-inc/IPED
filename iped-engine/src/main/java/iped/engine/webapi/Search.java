package iped.engine.webapi;

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

    @ApiOperation(value = "Search documents")
    @ApiResponses({
        @ApiResponse(code = 404, message = "sourceID does not exist")
    })
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response doSearch() throws Exception {
        String escapeq = q.replaceAll("/", "\\\\/");
        List<DocIDJSON> docs = new ArrayList<DocIDJSON>();
        if (sourceID == null || sourceID.equals("")) {
            IPEDSearcher searcher = new IPEDSearcher(Sources.multiSource, escapeq);
            IMultiSearchResult result = searcher.multiSearch();
            for (IItemId id : result.getIterator()) {
                docs.add(new DocIDJSON(Sources.sourceIntToString.get(id.getSourceId()), id.getId()));
            }
        } else {
            if (!Sources.sourceStringToInt.containsKey(sourceID)) {
                return Response.status(Response.Status.NOT_FOUND)
                        .type(MediaType.TEXT_PLAIN)
                        .entity("source not found: " + sourceID).build();
            }
            IPEDSource source = (IPEDSource) Sources.getSource(sourceID);
            IIPEDSearcher searcher = new IPEDSearcher(source, escapeq);
            SearchResult result = searcher.search();
            for (int id : result.getIds()) {
                docs.add(new DocIDJSON(sourceID, id));
            }
        }

        return Response.ok(new SourceToIDsJSON(docs)).build();
    }
}
