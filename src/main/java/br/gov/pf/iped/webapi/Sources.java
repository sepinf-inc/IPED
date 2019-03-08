package br.gov.pf.iped.webapi;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;
import org.sleuthkit.datamodel.TskCoreException;

import dpf.sp.gpinf.indexer.search.IPEDMultiSource;
import dpf.sp.gpinf.indexer.search.IPEDSource;

@Path("sources")
public class Sources {
	public static IPEDMultiSource multiSource = null;
	public static void init(String urlToAskSources) throws IOException, ParseException {
		ArrayList<IPEDSource> sources = new ArrayList<IPEDSource>(); 
		JSONArray arr = askSources(urlToAskSources);
		for (Object object : arr) {
			JSONObject jsonobj = (JSONObject)object;
			String path = (String)jsonobj.get("path");

			IPEDSource source = new IPEDSource(new File(path));
			sources.add(source);
		}

		multiSource = new IPEDMultiSource(sources);
	}


	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public static String listSources() throws TskCoreException, IOException{
		JSONArray data = new JSONArray();
		int sourceId = -1;
		for (IPEDSource source : multiSource.getAtomicSources()) {
				sourceId = source.getSourceId();
				data.add(getonejson(sourceId));
		}
		JSONObject json = new JSONObject();
		JSONObject links = new JSONObject();
		json.put("data", data);
		json.put("links", links);
		links.put("self", "/sources");
		return json.toString();
	}

	@GET
	@Path("{sourceID}")
	@Produces(MediaType.APPLICATION_JSON)
	public static String getone(@PathParam("sourceID") int sourceID) throws IOException, TskCoreException{
		return getonejson(Integer.valueOf(sourceID)).toString();
	}
	public static JSONObject getonejson(int sourceID) throws IOException, TskCoreException{
		IPEDSource source = get(sourceID);
		JSONObject json = new JSONObject();
		JSONObject relationships = new JSONObject();
		JSONObject links = new JSONObject();
		
		json.put("id", sourceID);
		json.put("path", source.getCaseDir().toString());
		json.put("links", links);
		links.put("self", "/sources/" + sourceID);
		json.put("relationships", relationships);
		relationships.put("docs", "/sources/" + sourceID + "/docs/");
		return json;
	}
	
	public static IPEDSource get(int sourceID) {
		return multiSource.getAtomicSourceBySourceId(sourceID);
	}

	private static JSONArray askSources(String urlToAskSources) throws MalformedURLException, IOException, ParseException{
		InputStream in;
		JSONArray result = new JSONArray();
		if ((new File(urlToAskSources)).exists()){
			in = new FileInputStream(urlToAskSources);
		}else{
			in = (new URL(urlToAskSources)).openConnection().getInputStream();
		}
		try {
			result = (JSONArray)JSONValue.parseWithException(new InputStreamReader(in));
		}finally{
			in.close();
		}
		return result;
	}
}

