package br.gov.pf.iped.webapi;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.json.simple.JSONObject;

@Path("")
public class Root {
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public static String root(){
		JSONObject json = new JSONObject();
		JSONObject relationships = new JSONObject();
		JSONObject links = new JSONObject();
		
		json.put("links", links);
		links.put("self", "/");
		json.put("relationships", relationships);
		relationships.put("sources", "/sources");
		relationships.put("search", "/search");
		relationships.put("bookmarks", "/bookmarks");
		relationships.put("categories", "/categories");
		relationships.put("selection", "/selection");
		return json.toString();
	}}
