package dpf.sp.gpinf.indexer.search;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiReader;

import dpf.sp.gpinf.indexer.analysis.AppAnalyzer;
import dpf.sp.gpinf.indexer.desktop.Marcadores;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.util.VersionsMap;
import gpinf.dev.data.EvidenceFile;

public class IPEDMultiSource extends IPEDSource{
	
	List<IPEDSource> cases = Collections.synchronizedList(new ArrayList<IPEDSource>());
	
	public IPEDMultiSource(File casePathsFile) {
		
		super(casePathsFile.getParentFile());
		
		try {
			byte[] bytes = Files.readAllBytes(casePathsFile.toPath());
			//BOM test
			if(bytes[0] == (byte)0xEF && bytes[1] == (byte)0xBB && bytes[2] == (byte)0xBF)
				bytes[0] = bytes[1] = bytes[2] = 0;
			
			String content = new String(bytes, "UTF-8");
			for(String pathStr : content.split("\n")){
				File path = new File(pathStr.trim());
				if(!new File(path, MODULE_DIR).exists())
					continue;
				System.out.println("Loading " + pathStr.trim());
				IPEDSource icase = new IPEDSource(path);
				cases.add(icase);
			}
			
			openIndex();
			
            int baseDoc = 0;
            int baseId = 0;
            ids = new int[reader.maxDoc()];
            for(IPEDSource iCase : cases){
            	for (int i = 0; i < iCase.reader.maxDoc(); i++)
            		ids[baseDoc + i] = iCase.ids[i] + baseId;
            	baseDoc += iCase.reader.maxDoc();
            	baseId += iCase.lastId + 1;
            }
            lastId = baseId - 1;
            	
            invertIdToLuceneIdArray();
			
            for(IPEDSource iCase : cases)
            	totalItens += iCase.totalItens;
			
            baseId = 0;
			splitedDocs = new HashSet<Integer>();
			textSizes = new int[lastId + 1];
			for(IPEDSource iCase : cases){
				for(Integer i : iCase.splitedDocs)
					splitedDocs.add(i + baseId);
				for (int i = 0; i < iCase.lastId + 1; i++)
					textSizes[baseId + i] = iCase.textSizes[i];
				iCase.textSizes = null;
				baseId += iCase.lastId + 1;
			}
			
			viewToRawMap = new VersionsMap(0);
			
			for(IPEDSource iCase : cases)
				for(String category : iCase.categories)
					if(!categories.contains(category))
						categories.add(category);
			
			marcadores = new Marcadores(this, this.getCaseDir());
			
			analyzer = AppAnalyzer.get();
			
			for(IPEDSource iCase : cases)
				if(iCase.isFTKReport)
					isFTKReport = true;
			
			System.out.println("Loaded " + cases.size() + " cases.");

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private void openIndex() throws IOException{
		int i = 0;
		IndexReader[] readers = new IndexReader[cases.size()]; 
		for(IPEDSource iCase : cases)
			readers[i++] = iCase.reader;
		
		reader = new MultiReader(readers, false);
		System.out.println("MultiReader opened");
		
		openSearcher();
		
	}
	
	@Override
	public void reopen() throws IOException{
		reader.close();
		openIndex();
	}
	
	@Override
	public void close() {
		super.close();
		
		for(IPEDSource iCase : cases)
			try {
				iCase.reader.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
	}
	
	@Override
	public IPEDSource getAtomicCase(int luceneId){
		int maxDoc = 0;
		for(IPEDSource iCase : cases){
			maxDoc += iCase.reader.maxDoc();
			if(luceneId < maxDoc)
				return iCase;
		}
		return null;
	}
	
	@Override
	protected int getBaseLuceneId(IPEDSource atomicCase){
		int maxDoc = 0;
		for(IPEDSource iCase : cases){
			if(atomicCase == iCase)
				return maxDoc;
			maxDoc += iCase.reader.maxDoc();
		}
		return maxDoc;
	}
	
	@Override
	protected SearchResult rebaseLuceneIds(SearchResult resultsFromAtomicCase, IPEDSource atomicCase){
		SearchResult result = resultsFromAtomicCase.clone();
		int baseDoc = getBaseLuceneId(atomicCase);
		for(int i = 0; i < result.docs.length; i++)
			result.docs[i] += baseDoc;
		return result;
	}
	
	@Override
	public EvidenceFile getItemByLuceneID(int luceneId){
		try {
			Document doc = searcher.doc(luceneId);
			IPEDSource atomicCase = getAtomicCase(luceneId);
			EvidenceFile item = IndexItem.getItem(doc, atomicCase.getModuleDir(), atomicCase.sleuthCase, false);
			return item;
			
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

}
