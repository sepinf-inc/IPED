package br.gov.pf.iped.webapi;

import java.io.IOException;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import dpf.sp.gpinf.indexer.search.ItemId;
import dpf.sp.gpinf.indexer.search.MultiMarcadores;
import dpf.sp.gpinf.indexer.search.SearchResult;

@Path("sources/{sourceID}/docs")
public class Docs {

	@DefaultValue("") @QueryParam("q") String q;
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public String doSearch(@PathParam("sourceID") final int sourceID) throws Exception{
    	String escapeq = q.replaceAll("/", "\\\\/");
		IPEDSource source = Sources.get(sourceID); 
    	IPEDSearcher searcher = new IPEDSearcher(source, escapeq);
    	
    	SearchResult result = searcher.search();
		JSONArray data = new JSONArray();
		for (int i = 0; i < result.getLength(); i++) {
			data.add(result.getId(i));
		}

		JSONObject json = new JSONObject();
		JSONObject links = new JSONObject();
		json.put("data", data);
		json.put("links", links);
		links.put("self", "/sources/" + sourceID + "/docs?q=" + q);

		return json.toString();
	}

	@GET
	@Path("{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public static String properties(@PathParam("sourceID") int sourceID, @PathParam("id") int id) throws IOException{
		IPEDSource source = Sources.get(sourceID);
		int luceneID = source.getLuceneId(id);
		Document doc = source.getReader().document(luceneID);
		
		JSONObject json = new JSONObject();
		JSONObject data = new JSONObject();
		JSONObject links = new JSONObject();
		JSONObject relationships = new JSONObject();
		json.put("links", links);
		links.put("self", "/sources/" + sourceID + "/docs/" + id);
		json.put("relationships", relationships);
		relationships.put("content", "/sources/" + sourceID + "/docs/" + id + "/content");
		relationships.put("text", "/sources/" + sourceID + "/docs/" + id + "/text");
		json.put("data", data);
		json.put("sourceID", sourceID);
		json.put("id", id); 
		json.put("luceneID", luceneID); 
		for (IndexableField field : doc.getFields()) {
		    String[] values = doc.getValues(field.name());
		    if(values.length == 1) {
		        data.put(field.name(), values[0]);
		    }else {
		        JSONArray array = new JSONArray();
		        for(String v : values)
		            array.add(v);
		        data.put(field.name(), array);
		    }
		}
        JSONArray bookmarks = new JSONArray();
        for (String b : source.getMarcadores().getLabelList(id)) {
            bookmarks.add(b);
        }
        data.put("bookmarks", bookmarks);
        
		return json.toString();
	}

//	@POST
//	@Produces(MediaType.APPLICATION_JSON)
//	public static StreamingOutput getmanydocs(String docs){
//		final JSONArray arr = (JSONArray)JSONValue.parse(docs);
//
//		return  new StreamingOutput() {
//			@Override
//			public void write(OutputStream output) throws IOException, WebApplicationException {
//				output.write("[".getBytes());
//				String comma = "";
//				for (Object object: arr) {
//					output.write(comma.getBytes());
//					comma = ",";
//					JSONArray item = (JSONArray)object;
//					String src = (String)item.get(0);
//					long docid = (Long)item.get(1);
//					output.write(properties(src, (int)docid).getBytes());
//				}
//				output.write("]".getBytes());
//			}
//		};
//	}
}
