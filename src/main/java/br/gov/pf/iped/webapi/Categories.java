package br.gov.pf.iped.webapi;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import io.swagger.annotations.Api;

@Api(value="Categories")
@Path("categories")
public class Categories {
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String get() throws Exception{
        
        JSONArray data = new JSONArray();
        for (String category : Sources.multiSource.getCategories()) {
            data.add(category);
        }
        
        JSONObject json = new JSONObject();
        json.put("data", data);

        return json.toString();
    }   
}