package br.gov.pf.iped.webapi;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import br.gov.pf.iped.webapi.json.DocIDJSON;
import br.gov.pf.iped.webapi.json.SourceToIDsJSON;
import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import dpf.sp.gpinf.indexer.search.ItemId;
import dpf.sp.gpinf.indexer.search.MultiSearchResult;
import dpf.sp.gpinf.indexer.search.SearchResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@Api(value="Search")
@Path("search")
public class Search {


	@DefaultValue("") @QueryParam("q") String q;
	@DefaultValue("") @QueryParam("sourceID") String sourceID;
	@ApiOperation(value="Search documents")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public SourceToIDsJSON doSearch() throws Exception{
    	String escapeq = q.replaceAll("/", "\\\\/");
        List<DocIDJSON> docs = new ArrayList<DocIDJSON>();
    	if (sourceID.equals("")) { 
    		IPEDSearcher searcher = new IPEDSearcher(Sources.multiSource, escapeq);
        	MultiSearchResult result = searcher.multiSearch();
            for (ItemId id : result.getIterator()) {
            	docs.add(new DocIDJSON(Sources.sourceIntToString.get(id.getSourceId()), id.getId()));
            }
    	} else {
    		IPEDSource source = Sources.getSource(sourceID); 
    		IPEDSearcher searcher = new IPEDSearcher(source, escapeq);
        	SearchResult result = searcher.search();
        	for (int id: result.getIds()) {
            	docs.add(new DocIDJSON(sourceID, id));
			}
    	}    	
        
        return new SourceToIDsJSON(docs);
	}	
}

