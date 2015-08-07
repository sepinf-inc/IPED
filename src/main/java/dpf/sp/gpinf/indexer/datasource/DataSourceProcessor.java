package dpf.sp.gpinf.indexer.datasource;

import gpinf.dev.data.CaseData;

import java.io.File;
import java.util.List;

import dpf.sp.gpinf.indexer.CmdLineArgs;

public abstract class DataSourceProcessor {
	
	CaseData caseData;
	boolean listOnly;
	File output;

	public DataSourceProcessor(CaseData caseData, File output, boolean listOnly) {
		this.caseData = caseData;
		this.listOnly = listOnly;
		this.output = output;
	}
	
	public abstract boolean isSupported(File datasource);
	
	public abstract int process(File datasource) throws Exception;
	
	public String currentDirectory(){
		return null;
	}
	
	public String getEvidenceName(File datasource){
		CmdLineArgs cmdArgs = ((CmdLineArgs)caseData.getCaseObject(CmdLineArgs.class.getName()));
		List<String> params = cmdArgs.getCmdArgs().get(CmdLineArgs.ALL_ARGS);
		for(int i = 0; i < params.size(); i++)
			if(params.get(i).equals("-d") && datasource.equals(new File(params.get(i+1))) && 
					i+2 < params.size() && params.get(i+2).equals("-dname"))
				return params.get(i+3);
		
		return null;
	}
}
