package dpf.sp.gpinf.indexer.process.task;

import java.util.Date;

import gpinf.dev.data.EvidenceFile;
import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.process.ItemProducer;
import dpf.sp.gpinf.indexer.process.Manager;
import dpf.sp.gpinf.indexer.process.Worker;
import dpf.sp.gpinf.indexer.process.task.ComputeHashTask.HashValue;

public class CheckDuplicateTask extends AbstractTask{
	
	private Manager manager;
	
	public CheckDuplicateTask(Worker worker){
		super(worker);
		this.manager = worker.manager;
	}

	public void process(EvidenceFile evidence){
		
		// Verificação de duplicados
		String hash = evidence.getHash();
		if(!evidence.timeOut){
			if (hash == null)
				evidence.setPrimaryHash(true);
			else{
				HashValue hashValue = new HashValue(hash);
				synchronized (manager.hashMap) {
					if(!manager.hashMap.containsKey(hashValue)){
						manager.hashMap.put(hashValue, hashValue);
						evidence.setPrimaryHash(true);
					}else
						evidence.setPrimaryHash(false);
						
				}
			}
		}
		
		if(Configuration.ignoreDuplicates && !evidence.isPrimaryHash() && !evidence.isDir() && !evidence.isRoot() && !ItemProducer.indexerReport){
			evidence.setToIgnore(true);
			if (evidence.isSubItem()) {
				if (!evidence.getFile().delete())
					System.out.println(new Date() + "\t[AVISO]\t" + Thread.currentThread().getName() + " Falha ao deletar " + evidence.getFile().getAbsolutePath());
			}
		}
		
	}

	@Override
	public void init() throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void finish() throws Exception {
		// TODO Auto-generated method stub
		
	}
	
}
