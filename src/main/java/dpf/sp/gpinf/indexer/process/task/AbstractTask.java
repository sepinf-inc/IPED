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
	
	abstract public void init() throws Exception;
	
	abstract public void process(EvidenceFile evidence) throws Exception;
	
	abstract public void finish() throws Exception;

}
