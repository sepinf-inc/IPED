package dpf.sp.gpinf.indexer.process.task;

import gpinf.dev.data.EvidenceFile;

import java.io.File;
import java.util.Properties;

import dpf.sp.gpinf.indexer.process.Worker;

public abstract class AbstractTask {
	
	protected Worker worker;
	
	public AbstractTask(){
	}
	
	public AbstractTask(Worker worker){
		this.worker = worker;
	}
	
	abstract public void init(final Properties confProps, File confDir) throws Exception;
	
	abstract public void process(EvidenceFile evidence) throws Exception;
	
	abstract public void finish() throws Exception;

}
