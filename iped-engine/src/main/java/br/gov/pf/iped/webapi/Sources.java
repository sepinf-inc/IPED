package br.gov.pf.iped.webapi;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;
import org.sleuthkit.datamodel.TskCoreException;

import br.gov.pf.iped.webapi.json.DataListJSON;
import br.gov.pf.iped.webapi.json.SourceJSON;
import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.search.IPEDMultiSource;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import iped3.IIPEDSource;

@Api(value = "Sources")
@Path("sources")
public class Sources {
    public static IPEDMultiSource multiSource = null;
    public static Map<Integer, String> sourceIntToString;
    public static Map<String, Integer> sourceStringToInt;
    public static Map<String, String> sourcePathToStringID;

    public static void init(String urlToAskSources) throws IOException, ParseException {
        sourceIntToString = new HashMap<Integer, String>();
        sourceStringToInt = new HashMap<String, Integer>();
        sourcePathToStringID = new HashMap<String, String>();

        boolean confInited = false;
        List<IIPEDSource> sources = new ArrayList<IIPEDSource>();
        JSONArray arr = askSources(urlToAskSources);
        for (Object object : arr) {
            JSONObject jsonobj = (JSONObject) object;
            String id = (String) jsonobj.get("id");
            File file = new File((String) jsonobj.get("path"));

            sourcePathToStringID.put(file.toString(), id);

            if (!confInited) {
                Configuration.getInstance().loadConfigurables(file + File.separator + "indexador", true); //$NON-NLS-1$
                confInited = true;
            }

            IIPEDSource source = new IPEDSource(file);
            sources.add(source);
        }

        multiSource = new IPEDMultiSource(sources);
        // filling maps using path, to avoid relying on provided order
        for (int i = 0; i < multiSource.getAtomicSources().size(); i++) {
            IIPEDSource source = multiSource.getAtomicSourceBySourceId(i);
            String path = source.getCaseDir().toString();
            String id = sourcePathToStringID.get(path);
            if (sourceStringToInt.containsKey(id)) {
                throw new RuntimeException("duplicated id: " + id);
            }
            sourceStringToInt.put(id, i);
            sourceIntToString.put(i, id);
        }
    }

    public static IIPEDSource getSource(String sourceID) {
        int id = sourceStringToInt.get(sourceID);
        return multiSource.getAtomicSourceBySourceId(id);
    }

    @ApiOperation(value = "List sources")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public static DataListJSON<SourceJSON> listSources() throws TskCoreException, IOException {
        List<SourceJSON> data = new ArrayList<SourceJSON>();
        for (IIPEDSource source : multiSource.getAtomicSources()) {
            int id = source.getSourceId();
            String sourceID = sourceIntToString.get(id);
            data.add(getone(sourceID));
        }
        return new DataListJSON<SourceJSON>(data);
    }

    @ApiOperation(value = "Add source")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public synchronized static Response addSource(@ApiParam(required = true) SourceJSON sourcejson) {
        String id = sourcejson.getId();
        String path = sourcejson.getPath();
        if (sourceStringToInt.containsKey(id)) {
            throw new RuntimeException("duplicated id: " + id);
        }
        sourcePathToStringID.put(path, id);

        List<IPEDSource> sources = multiSource.getAtomicSources();
        int last = sources.size();
        sources.add(new IPEDSource(new File(path)));
        if (last + 1 != sources.size()) {
            throw new RuntimeException("concurrency error adding source");
        }
        multiSource.init();
        IIPEDSource source = multiSource.getAtomicSourceBySourceId(last);
        String realpath = source.getCaseDir().toString();
        if (!path.equals(realpath)) {
            throw new RuntimeException("error adding source; expected " + path + " got " + realpath);
        }
        sourceStringToInt.put(id, last);
        sourceIntToString.put(last, id);

        return Response.ok().build();
    }

    @ApiOperation(value = "Get source's properties")
    @GET
    @Path("{sourceID}")
    @Produces(MediaType.APPLICATION_JSON)
    public static SourceJSON getone(@PathParam("sourceID") String sourceID) throws IOException, TskCoreException {
        SourceJSON result = new SourceJSON();
        IIPEDSource source = getSource(sourceID);
        result.setId(sourceID);
        result.setPath(source.getCaseDir().toString());
        return result;
    }

    private static JSONArray askSources(String urlToAskSources)
            throws MalformedURLException, IOException, ParseException {
        InputStream in;
        JSONArray result = new JSONArray();
        if ((new File(urlToAskSources)).exists()) {
            in = new FileInputStream(urlToAskSources);
        } else {
            in = (new URL(urlToAskSources)).openConnection().getInputStream();
        }
        try {
            result = (JSONArray) JSONValue.parseWithException(new InputStreamReader(in));
        } finally {
            in.close();
        }
        return result;
    }
}
