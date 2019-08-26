package dpf.sp.gpinf.indexer.process.task;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import dpf.sp.gpinf.indexer.search.IPEDSearcher;
import dpf.sp.gpinf.indexer.search.IPEDSource;
import iped3.IItem;
import jep.Jep;
import jep.JepException;
import jep.SharedInterpreter;

public class PythonTask extends AbstractTask{
    
    private static boolean finished;
    private static String scriptName;
    
    private File scriptFile;
    private Jep jep;
    
    private Properties confParams;
    private File confDir;

    public PythonTask(File scriptFile) {
        this.scriptFile = scriptFile;
        try {
            loadScript();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void loadScript() throws  JepException {
        
        if(scriptName == null) {
            try(Jep jep = getNewJep()){
                scriptName = (String) jep.invoke("getName");
            }
        }
        
    }
    
    private Jep getNewJep() throws JepException {
        Jep jep = new SharedInterpreter();
        jep.setInteractive(false);
        
        jep.set("caseData", this.caseData); //$NON-NLS-1$
        jep.set("moduleDir", this.output); //$NON-NLS-1$
        jep.set("worker", this.worker); //$NON-NLS-1$
        jep.set("stats", this.stats); //$NON-NLS-1$
        
        jep.runScript(scriptFile.getAbsolutePath());
        
        if(confParams != null && confDir != null) {
            jep.invoke("init", confParams, confDir);
        }
        
        return jep;
    }
    
    @Override
    public String getName() {
        return scriptName; //$NON-NLS-1$
    }

    @Override
    public void init(Properties confParams, File confDir) throws Exception {
        this.confParams = confParams;
        this.confDir = confDir;
    }

    @Override
    public void finish() throws Exception {
        
        if(finished)
            return;
        
        try (Jep jep = getNewJep();
             IPEDSource ipedCase = new IPEDSource(this.output.getParentFile(), worker.writer)){
            
            IPEDSearcher searcher = new IPEDSearcher(ipedCase);

            jep.set("ipedCase", ipedCase); //$NON-NLS-1$
            jep.set("searcher", searcher); //$NON-NLS-1$

            jep.invoke("finish"); //$NON-NLS-1$
            
        }finally {
            finished = true;
        }
        
    }

    @Override
    protected void process(IItem item) throws Exception {
        if(jep == null)
            jep = getNewJep();
        
        jep.invoke("process", item); //$NON-NLS-1$
    }

}
