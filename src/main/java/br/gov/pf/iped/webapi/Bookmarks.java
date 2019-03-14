package br.gov.pf.iped.webapi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import br.gov.pf.iped.webapi.models.DataListModel;
import br.gov.pf.iped.webapi.models.DocIDModel;
import br.gov.pf.iped.webapi.models.SourceToIDsModel;
import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.search.ItemId;
import dpf.sp.gpinf.indexer.search.MultiMarcadores;
import dpf.sp.gpinf.indexer.search.MultiSearchResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;


@Api(value="Bookmarks")
@Path("bookmarks")
public class Bookmarks {
  
	@ApiOperation(value="List bookmarks")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public DataListModel<String> getAll(){
        Set<String> bookmarks = Sources.multiSource.getMultiMarcadores().getLabelMap();
        String[]IDs = bookmarks.toArray(new String[0]);
        return new DataListModel<String>(IDs);
    }
    
	@ApiOperation(value="List bookmark documents")
    @GET
    @Path("{bookmark}")
    @Produces(MediaType.APPLICATION_JSON)
    public SourceToIDsModel get(@PathParam("bookmark") String bookmark) throws Exception{
        
        IPEDSearcher searcher = new IPEDSearcher(Sources.multiSource, "");
        MultiSearchResult result = searcher.multiSearch();
        result = Sources.multiSource.getMultiMarcadores().filtrarMarcadores(result, Collections.singleton(bookmark));
        
        List<DocIDModel> docs = new ArrayList<DocIDModel>();
        for (ItemId id : result.getIterator()) {
        	docs.add(new DocIDModel(id.getSourceId(), id.getId()));
        }
        
        return new SourceToIDsModel(docs);
    }
    
	@ApiOperation(value="Add documents to bookmark")
    @PUT
    @Path("{bookmark}/add")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response insertLabel(@PathParam("bookmark") String bookmark, 
    		@ApiParam(required=true) DocIDModel[] docs){
        MultiMarcadores mm = Sources.multiSource.getMultiMarcadores();
        List<ItemId> itemIds = new ArrayList<>();
        for (DocIDModel d: docs) {
        	itemIds.add(new ItemId(d.getSource(), d.getId()));
        }
        mm.addLabel(itemIds, bookmark);
        mm.saveState();
        return Response.ok().build();
    }
    
	@ApiOperation(value="Remove documents from bookmark")
    @PUT
    @Path("{bookmark}/remove")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response removeLabel(@PathParam("bookmark") String bookmark, 
    		@ApiParam(required=true) DocIDModel[] docs){
        MultiMarcadores mm = Sources.multiSource.getMultiMarcadores();
        List<ItemId> itemIds = new ArrayList<>();
        for (DocIDModel d: docs) {
        	itemIds.add(new ItemId(d.getSource(), d.getId()));
        }
        mm.removeLabel(itemIds, bookmark);
        mm.saveState();
        return Response.ok().build();
    }
    
	@ApiOperation(value="Create bookmark")
    @POST
    @Path("{bookmark}")
    public Response addLabel(@PathParam("bookmark") String bookmark) {
    	MultiMarcadores mm = Sources.multiSource.getMultiMarcadores();
    	mm.newLabel(bookmark);
    	mm.saveState();
    	return Response.ok().build();
    }  
    
	@ApiOperation(value="Delete bookmark")
    @DELETE
    @Path("{bookmark}")
    public Response delLabel(@PathParam("bookmark") String bookmark) {
        MultiMarcadores mm = Sources.multiSource.getMultiMarcadores();
        mm.delLabel(bookmark);
        mm.saveState();
        return Response.ok().build();
    }
    
	@ApiOperation(value="Rename bookmark")
    @PUT
    @Path("{old}/rename/{new}")
    public Response changeLabel(@PathParam("old") String oldLabel, @PathParam("new") String newLabel) {
        MultiMarcadores mm = Sources.multiSource.getMultiMarcadores();
        mm.changeLabel(oldLabel, newLabel);
        mm.saveState();
        return Response.ok().build();
    }
    
}