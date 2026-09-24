package iped.engine.webapi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import iped.data.IIPEDSource;
import iped.engine.webapi.json.DataListJSON;
import iped.engine.webapi.json.DocPropsJSON;

@Api(value = "Documents")
@Path("sources/{sourceID}/docs")
public class Docs {

    @ApiOperation(value = "Get document's properties")
    @GET
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public static DocPropsJSON properties(@PathParam("sourceID") String sourceID, @PathParam("id") int id)
            throws IOException {
        IIPEDSource source = Sources.getSource(sourceID);
        int luceneID = source.getLuceneId(id);
        Document doc = source.getReader().document(luceneID);

        DocPropsJSON result = new DocPropsJSON();
        result.setSource(sourceID);
        result.setId(id);
        result.setLuceneId(luceneID);
        Map<String, String[]> properties = new HashMap<String, String[]>();
        for (IndexableField field : doc.getFields()) {
            String[] values = doc.getValues(field.name());
            properties.put(field.name(), values);
        }
        result.setProperties(properties);

        result.setBookmarks(source.getBookmarks().getBookmarkList(id));
        result.setSelected(source.getBookmarks().isChecked(id));

        return result;
    }

    @ApiOperation(value = "Get properties for multiple documents in batch")
    @POST
    @Path("batch")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public static DataListJSON<DocPropsJSON> propertiesBatch(
            @PathParam("sourceID") String sourceID,
            @ApiParam(required = true) int[] ids) throws IOException {

        IIPEDSource source = Sources.getSource(sourceID);
        List<DocPropsJSON> resultList = new ArrayList<DocPropsJSON>();

        if (ids != null) {
            for (int id : ids) {
                try {
                    int luceneID = source.getLuceneId(id);
                    Document doc = source.getReader().document(luceneID);

                    DocPropsJSON result = new DocPropsJSON();
                    result.setSource(sourceID);
                    result.setId(id);
                    result.setLuceneId(luceneID);

                    Map<String, String[]> properties = new HashMap<String, String[]>();
                    for (IndexableField field : doc.getFields()) {
                        String[] values = doc.getValues(field.name());
                        properties.put(field.name(), values);
                    }
                    result.setProperties(properties);
                    result.setBookmarks(source.getBookmarks().getBookmarkList(id));
                    result.setSelected(source.getBookmarks().isChecked(id));

                    resultList.add(result);
                } catch (Exception e) {
                    // Ignore invalid IDs
                }
            }
        }
        return new DataListJSON<DocPropsJSON>(resultList);
    }
}
