package br.gov.pf.iped.webapi;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.search.ItemId;
import dpf.sp.gpinf.indexer.search.MultiSearchResult;

@Path("search")
public class Search {
	
	@DefaultValue("") @QueryParam("q") String q;
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public String doSearch() throws Exception{
    	String escapeq = q.replaceAll("/", "\\\\/");
    	IPEDSearcher searcher = new IPEDSearcher(Sources.multiSource, escapeq);
    	
    	MultiSearchResult result = searcher.multiSearch();
		JSONArray data = new JSONArray();
		for (int i = 0; i < result.getLength(); i++) {
			ItemId id = result.getItem(i);
			JSONObject item = new JSONObject();
			item.put("source", id.getSourceId());
			item.put("id", id.getId());
			data.add(item);
		}

		JSONObject json = new JSONObject();
		JSONObject links = new JSONObject();
		json.put("data", data);
		json.put("links", links);
		links.put("self", "/search?q=" + q);

		return json.toString();
	}	
}

