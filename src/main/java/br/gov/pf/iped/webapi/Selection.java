package br.gov.pf.iped.webapi;

import java.util.Collections;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.search.ItemId;
import dpf.sp.gpinf.indexer.search.MultiMarcadores;
import dpf.sp.gpinf.indexer.search.MultiSearchResult;
import io.swagger.annotations.Api;

@Api(value="Selection")
@Path("selection")
public class Selection {
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String get() throws Exception{
        
        IPEDSearcher searcher = new IPEDSearcher(Sources.multiSource, "");
        MultiSearchResult result = searcher.multiSearch();
        result = Sources.multiSource.getMultiMarcadores().filtrarSelecionados(result);
        
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
    @Path("add")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response add(String json) throws ParseException {
        MultiMarcadores mm = Sources.multiSource.getMultiMarcadores();
        List<ItemId> itemIds = Bookmarks.getItemIdFromJsonArray(json);
        for(ItemId item : itemIds)
            mm.setSelected(true, item, Sources.multiSource);
        mm.saveState();
        return Response.ok().build();
    }
    
    @PUT
    @Path("remove")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response remove(String json) throws ParseException {
        MultiMarcadores mm = Sources.multiSource.getMultiMarcadores();
        List<ItemId> itemIds = Bookmarks.getItemIdFromJsonArray(json);
        for(ItemId item : itemIds)
            mm.setSelected(false, item, Sources.multiSource);
        mm.saveState();
        return Response.ok().build();
    }

}
