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

import br.gov.pf.iped.webapi.models.DocIDModel;
import br.gov.pf.iped.webapi.models.SourceToIDsModel;
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
    public SourceToIDsModel get() throws Exception{
        
        IPEDSearcher searcher = new IPEDSearcher(Sources.multiSource, "");
        MultiSearchResult result = searcher.multiSearch();
        result = Sources.multiSource.getMultiMarcadores().filtrarSelecionados(result);
        
        List<DocIDModel> docs = new ArrayList<DocIDModel>();
        for (ItemId id : result.getIterator()) {
        	docs.add(new DocIDModel(id.getSourceId(), id.getId()));
        }
        
        return new SourceToIDsModel(docs);
    }
    
	@ApiOperation(value="Add documents to selection")
    @PUT
    @Path("add")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response add(@ApiParam(required=true) DocIDModel[] docs){
        MultiMarcadores mm = Sources.multiSource.getMultiMarcadores();
        for (DocIDModel d: docs) {
            mm.setSelected(true, new ItemId(d.getSource(), d.getId()), Sources.multiSource);
        }
        mm.saveState();
        return Response.ok().build();
    }
    
	@ApiOperation(value="Remove documents from selection")
    @PUT
    @Path("remove")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response remove(@ApiParam(required=true) DocIDModel[] docs){
        MultiMarcadores mm = Sources.multiSource.getMultiMarcadores();
        for (DocIDModel d: docs) {
            mm.setSelected(false, new ItemId(d.getSource(), d.getId()), Sources.multiSource);
        }
        mm.saveState();
        return Response.ok().build();
    }

}
