package dpf.sp.gpinf.indexer.process;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.index.IndexWriter;

import dpf.sp.gpinf.indexer.parsers.util.Item;
import dpf.sp.gpinf.indexer.parsers.util.ItemSearcher;
import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import dpf.sp.gpinf.indexer.search.SearchResult;

public class ItemSearcherImpl implements ItemSearcher{
	
	File caseFolder;
	IndexWriter iw;
	IPEDSource iSource;
	
	public ItemSearcherImpl(File caseFolder, IndexWriter iw){
		this.caseFolder = caseFolder;
		this.iw = iw;
	}

	@Override
	public List<Item> search(String luceneQuery) {
		
		List<Item> items = new ArrayList<Item>();
		try {
			if(iSource == null)
				iSource = new IPEDSource(caseFolder, iw);
			
			IPEDSearcher searcher = new IPEDSearcher(iSource, luceneQuery);
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
