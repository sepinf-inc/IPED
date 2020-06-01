package dpf.sp.gpinf.indexer.process.task.transcript;

import java.io.File;
import java.util.Properties;

import dpf.sp.gpinf.indexer.process.task.AbstractTask;
import dpf.sp.gpinf.indexer.util.UTF8Properties;
import iped3.IItem;

public class AudioTranscriptTask extends AbstractTask{
    
    private static final String IMPL_CLASS_KEY = "implementationClass";
    
    private AbstractTranscriptTask impl;

    @Override
    public void init(Properties confParams, File confDir) throws Exception {
        
        UTF8Properties props = new UTF8Properties();
        props.load(new File(confDir, AbstractTranscriptTask.CONF_FILE));
                
        String className = props.getProperty(IMPL_CLASS_KEY).trim();
        
        impl = (AbstractTranscriptTask) Class.forName(className).newInstance();
        impl.setWorker(worker);
        impl.init(confParams, confDir);
    }
    
    public boolean isEnabled() {
        return impl.isEnabled();
    }

    @Override
    public void finish() throws Exception {
        impl.finish();
    }

    @Override
    protected void process(IItem evidence) throws Exception {
        impl.process(evidence);
    }

}
