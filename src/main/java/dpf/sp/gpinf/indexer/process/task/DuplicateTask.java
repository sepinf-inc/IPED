package dpf.sp.gpinf.indexer.process.task;

import gpinf.dev.data.EvidenceFile;

import java.io.File;
import java.util.Date;
import java.util.HashMap;
import java.util.Properties;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.store.FSDirectory;

import dpf.sp.gpinf.indexer.process.IndexItem;
import dpf.sp.gpinf.indexer.process.ItemProducer;
import dpf.sp.gpinf.indexer.process.Manager;
import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.process.task.HashTask.HashValue;

/**
 * Tarefa de verificação de arquivos duplicados. Ignora o arquivo caso configurado.
 *
 */
public class DuplicateTask extends AbstractTask{
	
	public static String HASH_MAP = HashTask.class.getSimpleName() + "HashMap";
	
	private HashMap<HashValue, HashValue> hashMap;
	
	public static boolean ignoreDuplicates = false;
	
	private Manager manager;
	
	public DuplicateTask(Worker worker){
		super(worker);
		this.manager = worker.manager;
	}

	public void process(EvidenceFile evidence){
		
		// Verificação de duplicados
		String hash = evidence.getHash();
		if (hash != null){
			HashValue hashValue = new HashValue(hash);
			synchronized (hashMap) {
				if(!hashMap.containsKey(hashValue)){
					hashMap.put(hashValue, hashValue);
				}else
					evidence.setDuplicate(true);
					
			}
		}
		
		if(ignoreDuplicates && evidence.isDuplicate() && !evidence.isDir() && !evidence.isRoot() && !ItemProducer.indexerReport){
			evidence.setToIgnore(true);
			stats.incDuplicatesIgnored();
			if (evidence.isSubItem()) {
				if (!evidence.getFile().delete())
					System.out.println(new Date() + "\t[AVISO]\t" + Thread.currentThread().getName() + " Falha ao deletar " + evidence.getFile().getAbsolutePath());
			}
		}
		
	}

	@Override
	public void init(Properties confProps, File confDir) throws Exception {

		String value = confProps.getProperty("ignoreDuplicates");
		if (value != null)
			value = value.trim();
		if (value != null && !value.isEmpty())
			ignoreDuplicates = Boolean.valueOf(value);
		
		hashMap = (HashMap<HashValue, HashValue>) worker.caseData.getObjectMap().get(HASH_MAP);
		if(hashMap == null){
			hashMap = new HashMap<HashValue, HashValue>();
			worker.caseData.getObjectMap().put(HASH_MAP, hashMap);
			
			File indexDir = new File(worker.output, "index");
			if(indexDir.exists()){
				IndexReader reader = IndexReader.open(FSDirectory.open(indexDir));
				for (int i = 0; i < reader.maxDoc(); i++) {
					Document doc = reader.document(i);
					String hash = doc.get(IndexItem.HASH);
					if (hash != null){
						HashValue hValue = new HashValue(hash);
						hashMap.put(hValue, hValue);
					}
						
				}
				reader.close();
			}
		}
		
	}

	@Override
	public void finish() throws Exception {
		// TODO Auto-generated method stub
		
	}
	
}
