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
import dpf.sp.gpinf.indexer.search.MultiMarcadores;
import dpf.sp.gpinf.indexer.search.MultiSearchResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

@Api(value="Selection")
@Path("selection")
public class Selection {
    
	@ApiOperation(value="List selected documents")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public SourceToIDsJSON get() throws Exception{
        
        IPEDSearcher searcher = new IPEDSearcher(Sources.multiSource, "");
        MultiSearchResult result = searcher.multiSearch();
        result = Sources.multiSource.getMultiMarcadores().filtrarSelecionados(result);
        
        List<DocIDJSON> docs = new ArrayList<DocIDJSON>();
        for (ItemId id : result.getIterator()) {
        	docs.add(new DocIDJSON(Sources.sourceIntToString.get(id.getSourceId()), id.getId()));
        }
        
        return new SourceToIDsJSON(docs);
    }
    
	@ApiOperation(value="Add documents to selection")
    @PUT
    @Path("add")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response add(@ApiParam(required=true) DocIDJSON[] docs){
        MultiMarcadores mm = Sources.multiSource.getMultiMarcadores();
        for (DocIDJSON d: docs) {
            mm.setSelected(true, new ItemId(Sources.sourceStringToInt.get(d.getSource()), d.getId()), Sources.multiSource);
        }
        mm.saveState();
        return Response.ok().build();
    }
    
	@ApiOperation(value="Remove documents from selection")
    @PUT
    @Path("remove")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response remove(@ApiParam(required=true) DocIDJSON[] docs){
        MultiMarcadores mm = Sources.multiSource.getMultiMarcadores();
        for (DocIDJSON d: docs) {
            mm.setSelected(false, new ItemId(Sources.sourceStringToInt.get(d.getSource()), d.getId()), Sources.multiSource);
        }
        mm.saveState();
        return Response.ok().build();
    }

}
