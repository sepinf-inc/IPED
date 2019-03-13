package br.gov.pf.iped.webapi;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;

import br.gov.pf.iped.webapi.models.DocPropsModel;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@Api(value="Documents")
@Path("sources/{sourceID}/docs")
public class Docs {

	@ApiOperation(value="Get document's properties")
	@GET
	@Path("{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public static DocPropsModel properties(@PathParam("sourceID") int sourceID, @PathParam("id") int id) throws IOException{
		IPEDSource source = Sources.multiSource.getAtomicSourceBySourceId(sourceID);
		int luceneID = source.getLuceneId(id);
		Document doc = source.getReader().document(luceneID);
		
		DocPropsModel result = new DocPropsModel();
		result.setSource(sourceID);
		result.setId(id);
		result.setLuceneId(luceneID);
		Map<String, String[]> properties = new HashMap<String, String[]>();
		for (IndexableField field : doc.getFields()) {
		    String[] values = doc.getValues(field.name());
	        properties.put(field.name(), values);
		}
		result.setProperties(properties);
		
        result.setBookmarks(source.getMarcadores().getLabelList(id));
        result.setSelected(source.getMarcadores().isSelected(id));
        
		return result;
	}
}
