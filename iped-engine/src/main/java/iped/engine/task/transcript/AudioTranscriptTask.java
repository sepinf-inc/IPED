package iped.engine.task.transcript;

import java.util.Arrays;
import java.util.List;

import iped.configuration.Configurable;
import iped.data.IItem;
import iped.engine.config.AudioTranscriptConfig;
import iped.engine.config.ConfigurationManager;
import iped.engine.task.AbstractTask;

public class AudioTranscriptTask extends AbstractTask {

    private AbstractTranscriptTask impl;
    private AbstractTranscriptTask implFallBack;
    private boolean enableClientFallBack = false;

    @Override
    public List<Configurable<?>> getConfigurables() {
        return Arrays.asList(new AudioTranscriptConfig());
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {
        AudioTranscriptConfig transcriptConfig = configurationManager.findObject(AudioTranscriptConfig.class);

        impl = (AbstractTranscriptTask) Class.forName(transcriptConfig.getClassName()).getDeclaredConstructor().newInstance();
        impl.setWorker(worker);
        impl.init(configurationManager);

        if (impl.getRequeueHeuristic() && impl.getClientTranscriptHelp()){
            String classNameFallBack = impl.getClassNameFallBack();
            if (classNameFallBack != null && !classNameFallBack.isEmpty()){                
                implFallBack = (AbstractTranscriptTask) Class.forName(classNameFallBack).getDeclaredConstructor().newInstance();
                if (implFallBack.isRemoteTask()){
                    implFallBack = null;
                    return;
                }
                enableClientFallBack = true;
                implFallBack.setWorker(worker);
                implFallBack.init(configurationManager);
            }
        }

    }

    public boolean isEnabled() {
        return impl.isEnabled();
    }

    @Override
    public void finish() throws Exception {
        impl.finish();
        if (enableClientFallBack){
            implFallBack.finish();
        }

    }

    @Override
    protected void process(IItem evidence) throws Exception {

        if(evidence.isFallBackTask() && enableClientFallBack){
            implFallBack.process(evidence);            
        }else{            
            impl.process(evidence);
        }

    }

}
