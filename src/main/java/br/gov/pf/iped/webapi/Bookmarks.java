package br.gov.pf.iped.webapi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.search.ItemId;
import dpf.sp.gpinf.indexer.search.MultiMarcadores;
import dpf.sp.gpinf.indexer.search.MultiSearchResult;

@Path("bookmarks")
public class Bookmarks {
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String getAll() throws Exception{
        
        JSONArray data = new JSONArray();
        Set<String> bookmarks = Sources.multiSource.getMultiMarcadores().getLabelMap(); 
        for (String b : bookmarks) {
            data.add(b);
        }
        
        JSONObject json = new JSONObject();
        json.put("data", data);

        return json.toString();
    }
    
    @GET
    @Path("{bookmark}")
    @Produces(MediaType.APPLICATION_JSON)
    public String get(@PathParam("bookmark") String bookmark) throws Exception{
        
        IPEDSearcher searcher = new IPEDSearcher(Sources.multiSource, "");
        MultiSearchResult result = searcher.multiSearch();
        result = Sources.multiSource.getMultiMarcadores().filtrarMarcadores(result, Collections.singleton(bookmark));
        
        JSONArray data = new JSONArray();
        for (ItemId id : result.getIterator()) {
            JSONObject item = new JSONObject();
            item.put("source", id.getSourceId());
            item.put("id", id.getId());
            data.add(item);
        }
        
        JSONObject json = new JSONObject();
        json.put("data", data);

        return json.toString();
    }
    
    @PUT
    @Path("add/{bookmark}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response insertLabel(@PathParam("bookmark") String bookmark, String json) throws ParseException {
        MultiMarcadores mm = Sources.multiSource.getMultiMarcadores();
        List<ItemId> itemIds = getItemIdFromJsonArray(json);
        mm.addLabel(itemIds, bookmark);
        mm.saveState();
        return Response.ok().build();
    }
    
    @PUT
    @Path("remove/{bookmark}")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response removeLabel(@PathParam("bookmark") String bookmark, String json) throws ParseException {
        MultiMarcadores mm = Sources.multiSource.getMultiMarcadores();
        List<ItemId> itemIds = getItemIdFromJsonArray(json);
        mm.removeLabel(itemIds, bookmark);
        mm.saveState();
        return Response.ok().build();
    }
    
    public static List<ItemId> getItemIdFromJsonArray(String json) throws ParseException{
        JSONArray list = (JSONArray)JSONValue.parseWithException(json);
        List<ItemId> itemIds = new ArrayList<>();
        for(Object o : list){
            JSONObject obj = (JSONObject)o;
            int sourceID = Integer.valueOf((String)obj.get("source"));
            for(Object id : (JSONArray)obj.get("ids")) {
                ItemId item = new ItemId(sourceID, Integer.valueOf((String)id));
                itemIds.add(item);
            }
        }
        return itemIds;
    }
    
    @PUT
    @Path("new/{bookmark}")
    public Response addLabel(@PathParam("bookmark") String bookmark) {
        MultiMarcadores mm = Sources.multiSource.getMultiMarcadores();
        mm.newLabel(bookmark);
        mm.saveState();
        return Response.ok().build();
    }
    
    @PUT
    @Path("del/{bookmark}")
    public Response delLabel(@PathParam("bookmark") String bookmark) {
        MultiMarcadores mm = Sources.multiSource.getMultiMarcadores();
        mm.delLabel(bookmark);
        mm.saveState();
        return Response.ok().build();
    }
    
    @PUT
    @Path("rename/{old}/{new}")
    public Response changeLabel(@PathParam("old") String oldLabel, @PathParam("new") String newLabel) {
        MultiMarcadores mm = Sources.multiSource.getMultiMarcadores();
        mm.changeLabel(oldLabel, newLabel);
        mm.saveState();
        return Response.ok().build();
    }
    
}