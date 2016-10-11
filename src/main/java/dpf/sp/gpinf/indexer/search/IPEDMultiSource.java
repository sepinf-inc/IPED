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
import dpf.sp.gpinf.indexer.desktop.App;
import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.util.VersionsMap;
import gpinf.dev.data.EvidenceFile;

public class IPEDMultiSource extends IPEDSource{
	
	List<IPEDSource> cases = Collections.synchronizedList(new ArrayList<IPEDSource>());
	
	public IPEDMultiSource(List<IPEDSource> sources) {
		super(null);
		this.cases = sources;
		init();
	}
	
	public IPEDMultiSource(File file){
		
		super(file.getParentFile());
		
		List<File> files;
		if(file.isDirectory())
			files = searchCasesinFolder(file);
		else
			files = loadCasesFromTxtFile(file);
		
		for(File src : files){
			System.out.println("Loading " + src.getAbsolutePath());
			IPEDSource icase = new IPEDSource(src);
			this.cases.add(icase);
		}
		init();
		
	}
	
	private List<File> loadCasesFromTxtFile(File file){
		
		ArrayList<File> files = new ArrayList<File>();
		try {
			byte[] bytes = Files.readAllBytes(file.toPath());
			//BOM test
			if(bytes[0] == (byte)0xEF && bytes[1] == (byte)0xBB && bytes[2] == (byte)0xBF)
				bytes[0] = bytes[1] = bytes[2] = 0;
			
			String content = new String(bytes, "UTF-8");
			for(String pathStr : content.split("\n")){
				File path = new File(pathStr.trim());
				if(!new File(path, MODULE_DIR).exists())
					continue;
				files.add(path);
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		return files;
	}
	
	private List<File> searchCasesinFolder(File folder){
		System.out.println("Searching cases in " + folder.getPath());
		ArrayList<File> files = new ArrayList<File>();
		File[] subFiles = folder.listFiles();
		if(subFiles != null)
			for(File file : subFiles){
				if(new File(file, MODULE_DIR).exists())
					files.add(file);
				else if(file.isDirectory())
					files.addAll(searchCasesinFolder(file));
			}
		return files;
	}
		
	private void init(){
		
		try{
			openIndex();
			
		} catch (IOException e) {
			e.printStackTrace();
		}

        for(IPEDSource iCase : cases)
        	totalItens += iCase.totalItens;
		
		for(IPEDSource iCase : cases)
			for(String category : iCase.categories)
				if(!categories.contains(category))
					categories.add(category);
		
		for(IPEDSource iCase : cases)
			for(String keyword : iCase.keywords)
				if(!keywords.contains(keyword))
					keywords.add(keyword);
		
		//marcadores = new Marcadores(this, this.getCaseDir());
		this.globalMarcadores = new MultiMarcadores(cases);
		
		analyzer = AppAnalyzer.get();
		
		for(IPEDSource iCase : cases)
			if(iCase.isFTKReport)
				isFTKReport = true;
		
		System.out.println("Loaded " + cases.size() + " cases.");
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
	
	final public IPEDSource getAtomicSource(int luceneId){
		int maxDoc = 0;
		for(IPEDSource iCase : cases){
			maxDoc += iCase.reader.maxDoc();
			if(luceneId < maxDoc)
				return iCase;
		}
		return null;
	}
	
	final public IPEDSource getAtomicSourceBySourceId(int sourceId){
		return cases.get(sourceId);
	}
	
	final public List<IPEDSource> getAtomicSources(){
		return this.cases;
	}
	
	private final int getBaseLuceneId(IPEDSource atomicCase){
		int maxDoc = 0;
		for(IPEDSource iCase : cases){
			if(atomicCase == iCase)
				return maxDoc;
			maxDoc += iCase.reader.maxDoc();
		}
		return maxDoc;
	}
	
	private LuceneSearchResult rebaseLuceneIds(LuceneSearchResult resultsFromAtomicCase, IPEDSource atomicCase){
		LuceneSearchResult result = resultsFromAtomicCase.clone();
		int baseDoc = getBaseLuceneId(atomicCase);
		for(int i = 0; i < result.docs.length; i++)
			result.docs[i] += baseDoc;
		return result;
	}
	
	@Override
	final public EvidenceFile getItemByID(int id){
		throw new RuntimeException("Use getItemByItemId() from " + this.getClass().getSimpleName());
	}
	
	final public EvidenceFile getItemByItemId(ItemId item){
		return getAtomicSourceBySourceId(item.getSourceId()).getItemByID(item.getId());
	}
	
	@Override
	final public EvidenceFile getItemByLuceneID(int luceneId){
		IPEDSource atomicCase = getAtomicSource(luceneId);
		luceneId -= getBaseLuceneId(atomicCase);
		return atomicCase.getItemByLuceneID(luceneId);
	}
	
	@Override
	final public int getId(int luceneId){
		throw new RuntimeException("Use getItemId() from " + this.getClass().getSimpleName());
	}
	
	final public ItemId getItemId(int luceneId){
		IPEDSource atomicSource = getAtomicSource(luceneId);
		int sourceId = atomicSource.getSourceId();
		int baseDoc = getBaseLuceneId(atomicSource);
		int id = atomicSource.getId(luceneId - baseDoc);
		return new ItemId(sourceId, id);
	}
	
	@Override
	final public int getLuceneId(int id){
		throw new RuntimeException("Use getLuceneId(ItemId) from " + this.getClass().getSimpleName());
	}
	
	final public int getLuceneId(ItemId id){
		IPEDSource atomicCase = getAtomicSourceBySourceId(id.getSourceId());
		int baseDoc = getBaseLuceneId(atomicCase);
		return atomicCase.getLuceneId(id.getId()) + baseDoc;
	}
	
	@Override
	boolean isSplited(int id){
		throw new RuntimeException("Forbidden call from " + this.getClass().getSimpleName());
	}
	
	@Override
	public int getTextSize(int id) {
		throw new RuntimeException("Forbidden call from " + this.getClass().getSimpleName());
	}
	
	@Override
	public int getLastId() {
		throw new RuntimeException("Forbidden call from " + this.getClass().getSimpleName());
	}

}
