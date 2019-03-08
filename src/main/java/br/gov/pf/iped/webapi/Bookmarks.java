package br.gov.pf.iped.webapi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

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
    @Path("get/{sourceID}/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public String get(@PathParam("sourceID") int sourceID, @PathParam("id") int id) throws Exception{
        
        MultiMarcadores mm = Sources.multiSource.getMultiMarcadores();
        ItemId item = new ItemId(sourceID, id);
        
        JSONArray data = new JSONArray();
        for (String b : mm.getLabelList(item)) {
            data.add(b);
        }
        
        JSONObject json = new JSONObject();
        json.put("data", data);

        return json.toString();
    }
    
    @GET
    @Path("get/{bookmark}")
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
    
    /*
    @GET
    @Path("add/{bookmark}/{sourceID}/{ids}")
    @Consumes(MediaType.APPLICATION_JSON)
    public void createLabel(@PathParam("bookmark") String bookmark, @PathParam("sourceID") int sourceID, @PathParam("ids") String ids) {
        
        MultiMarcadores mm = Sources.multiSource.getMultiMarcadores();
        
        List<ItemId> itemIds = new ArrayList<>();
        for(String id : ids.split(",")) {
            ItemId item = new ItemId(sourceID, Integer.valueOf(id));
            itemIds.add(item);
        }
        
        mm.addLabel(itemIds, bookmark);
        mm.saveState();
    }
    */
    
}