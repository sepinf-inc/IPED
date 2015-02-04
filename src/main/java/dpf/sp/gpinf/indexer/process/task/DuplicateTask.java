package dpf.sp.gpinf.indexer.process.task;

import gpinf.dev.data.EvidenceFile;

import java.io.File;
import java.util.Date;
import java.util.Properties;

import dpf.sp.gpinf.indexer.process.ItemProducer;
import dpf.sp.gpinf.indexer.process.Manager;
import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.process.task.HashTask.HashValue;

public class DuplicateTask extends AbstractTask{
	
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
			synchronized (manager.hashMap) {
				if(!manager.hashMap.containsKey(hashValue)){
					manager.hashMap.put(hashValue, hashValue);
				}else
					evidence.setDuplicate(true);
					
			}
		}
		
		if(ignoreDuplicates && evidence.isDuplicate() && !evidence.isDir() && !evidence.isRoot() && !ItemProducer.indexerReport){
			evidence.setToIgnore(true);
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
		
	}

	@Override
	public void finish() throws Exception {
		// TODO Auto-generated method stub
		
	}
	
}
