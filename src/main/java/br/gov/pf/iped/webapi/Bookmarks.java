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

import org.apache.cxf.jaxrs.ext.PATCH;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import br.gov.pf.iped.webapi.models.DataListModel;
import br.gov.pf.iped.webapi.models.DocumentModel;
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
    public DataListModel<String> getAll() throws Exception{
        Set<String> bookmarks = Sources.multiSource.getMultiMarcadores().getLabelMap();
        String[]IDs = bookmarks.toArray(new String[0]);
        return new DataListModel<String>(IDs);
    }
    
	@ApiOperation(value="List bookmark items")
    @GET
    @Path("{bookmark}")
    @Produces(MediaType.APPLICATION_JSON)
    public DataListModel<DocumentModel> get(@PathParam("bookmark") String bookmark) throws Exception{
        
        IPEDSearcher searcher = new IPEDSearcher(Sources.multiSource, "");
        MultiSearchResult result = searcher.multiSearch();
        result = Sources.multiSource.getMultiMarcadores().filtrarMarcadores(result, Collections.singleton(bookmark));
        
        DocumentModel[] docs = new DocumentModel[result.getLength()];
        int i = 0;
        for (ItemId id : result.getIterator()) {
        	docs[i++] = new DocumentModel(id.getSourceId(), id.getId());
        }
        
        return new DataListModel<DocumentModel>(docs);
    }
    
	@ApiOperation(value="Add items to bookmark")
    @POST
    @Path("{bookmark}/add")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response insertLabel(@PathParam("bookmark") String bookmark, 
    		@ApiParam(required=true) DocumentModel[] docs) throws ParseException {
        MultiMarcadores mm = Sources.multiSource.getMultiMarcadores();
        List<ItemId> itemIds = new ArrayList<>();
        for (DocumentModel d: docs) {
        	itemIds.add(new ItemId(d.getSource(), d.getId()));
        }
        mm.addLabel(itemIds, bookmark);
        mm.saveState();
        return Response.ok().build();
    }
    
	@ApiOperation(value="Remove items from bookmark")
    @PATCH
    @Path("{bookmark}/remove")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response removeLabel(@PathParam("bookmark") String bookmark, 
    		@ApiParam(required=true) DocumentModel[] docs) throws ParseException {
        MultiMarcadores mm = Sources.multiSource.getMultiMarcadores();
        List<ItemId> itemIds = new ArrayList<>();
        for (DocumentModel d: docs) {
        	itemIds.add(new ItemId(d.getSource(), d.getId()));
        }
        mm.removeLabel(itemIds, bookmark);
        mm.saveState();
        return Response.ok().build();
    }
    
    public static List<ItemId> getItemIdFromJsonArray(String json) throws ParseException{
        JSONArray list = (JSONArray)JSONValue.parseWithException(json);
        List<ItemId> itemIds = new ArrayList<>();
        for(Object o : list){
            JSONObject obj = (JSONObject)o;
            int sourceID = (int)(long)obj.get("source");
            for(Object id : (JSONArray)obj.get("ids")) {
                ItemId item = new ItemId(sourceID, (int)(long)id);
                itemIds.add(item);
            }
        }
        return itemIds;
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