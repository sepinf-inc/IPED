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
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;

@Api(value="Search")
@Path("search")
public class Search {


	@DefaultValue("") @QueryParam("q") String q;
	@DefaultValue("-1") @QueryParam("sourceID") String sourceIDstr;
	@ApiOperation(value="Search documents")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public SourceToIDsJSON doSearch() throws Exception{
    	String escapeq = q.replaceAll("/", "\\\\/");
    	IPEDSearcher searcher;
    	int sourceID = Integer.parseInt(sourceIDstr);
    	if (sourceID == -1) { 
    		searcher = new IPEDSearcher(Sources.multiSource, escapeq);
    	} else {
    		IPEDSource source = Sources.multiSource.getAtomicSourceBySourceId(sourceID); 
    		searcher = new IPEDSearcher(source, escapeq);
    	}    	
    	MultiSearchResult result = searcher.multiSearch();
        List<DocIDJSON> docs = new ArrayList<DocIDJSON>();
        for (ItemId id : result.getIterator()) {
        	docs.add(new DocIDJSON(id.getSourceId(), id.getId()));
        }
        
        return new SourceToIDsJSON(docs);
	}	
}

