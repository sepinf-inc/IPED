package br.gov.pf.iped.webapi;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import br.gov.pf.iped.webapi.json.DocIDJSON;
import br.gov.pf.iped.webapi.json.SourceToIDsJSON;
import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.search.ItemId;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import iped3.IItemId;
import iped3.search.IIPEDSearcher;
import iped3.search.IMultiBookmarks;
import iped3.search.IMultiSearchResult;

@Api(value = "Selection")
@Path("selection")
public class Selection {

    @ApiOperation(value = "List selected documents")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public SourceToIDsJSON get() throws Exception {

        IIPEDSearcher searcher = new IPEDSearcher(Sources.multiSource, "");
        IMultiSearchResult result = searcher.multiSearch();
        result = Sources.multiSource.getMultiMarcadores().filterSelected(result);

        List<DocIDJSON> docs = new ArrayList<DocIDJSON>();
        for (IItemId id : result.getIterator()) {
            docs.add(new DocIDJSON(Sources.sourceIntToString.get(id.getSourceId()), id.getId()));
        }

        return new SourceToIDsJSON(docs);
    }

    @ApiOperation(value = "Add documents to selection")
    @PUT
    @Path("add")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response add(@ApiParam(required = true) DocIDJSON[] docs) {
        IMultiBookmarks mm = Sources.multiSource.getMultiMarcadores();
        for (DocIDJSON d : docs) {
            mm.setSelected(true, new ItemId(Sources.sourceStringToInt.get(d.getSource()), d.getId()));
        }
        mm.saveState();
        return Response.ok().build();
    }

    @ApiOperation(value = "Remove documents from selection")
    @PUT
    @Path("remove")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response remove(@ApiParam(required = true) DocIDJSON[] docs) {
        IMultiBookmarks mm = Sources.multiSource.getMultiMarcadores();
        for (DocIDJSON d : docs) {
            mm.setSelected(false, new ItemId(Sources.sourceStringToInt.get(d.getSource()), d.getId()));
        }
        mm.saveState();
        return Response.ok().build();
    }

}
