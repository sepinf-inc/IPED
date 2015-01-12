package dpf.sp.gpinf.indexer.process.task;

import dpf.sp.gpinf.indexer.process.Worker;
import gpinf.dev.data.EvidenceFile;

public abstract class AbstractTask {
	
	protected Worker worker;
	
	public AbstractTask(){
	}
	
	public AbstractTask(Worker worker){
		this.worker = worker;
	}
	
	public void init(){
		
	}
	
	abstract public void process(EvidenceFile evidence) throws Exception;
	
	
	public void finish(){
		
	}

}
