package dpf.sp.gpinf.indexer.process;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.index.IndexWriter;

import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import dpf.sp.gpinf.indexer.search.SearchResult;
import iped3.io.ItemBase;
import iped3.search.ItemSearcher;

public class ItemSearcherImpl implements ItemSearcher{
	
	File caseFolder;
	IndexWriter iw;
	IPEDSource iSource;
	
	public ItemSearcherImpl(File caseFolder, IndexWriter iw){
		this.caseFolder = caseFolder;
		this.iw = iw;
	}

	@Override
	public List<ItemBase> search(String luceneQuery) {
		
		List<ItemBase> items = new ArrayList<ItemBase>();
		try {
			if(iSource == null)
				iSource = new IPEDSource(caseFolder, iw);
			
			IPEDSearcher searcher = new IPEDSearcher(iSource, luceneQuery);
			searcher.setTreeQuery(true);
			searcher.setNoScoring(true);
			SearchResult result = searcher.search();
			
			for(int i = 0; i < result.getLength();i ++){
				int id = result.getId(i);
				items.add(iSource.getItemByID(id));
			}
			
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return items;
	}
	
	

}
