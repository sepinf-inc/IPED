package br.gov.pf.iped.webapi;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

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

import br.gov.pf.iped.webapi.models.DataListModel;
import br.gov.pf.iped.webapi.models.SourceModel;
import dpf.sp.gpinf.indexer.search.IPEDMultiSource;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@Api(value="Sources")
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

	@ApiOperation(value="List sources")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public static DataListModel<SourceModel> listSources() throws TskCoreException, IOException{
		List<SourceModel> data = new ArrayList<SourceModel>();
		for (IPEDSource source : multiSource.getAtomicSources()) {
				data.add(getone(source.getSourceId()));
		}
		return new DataListModel<SourceModel>(data);
	}

	@ApiOperation(value="Get source's properties")
	@GET
	@Path("{sourceID}")
	@Produces(MediaType.APPLICATION_JSON)
	public static SourceModel getone(@PathParam("sourceID") int sourceID) throws IOException, TskCoreException{
		SourceModel result = new SourceModel();
		IPEDSource source = multiSource.getAtomicSourceBySourceId(sourceID);
		result.setId(sourceID);
		result.setPath(source.getCaseDir().toString());
		return result;
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

