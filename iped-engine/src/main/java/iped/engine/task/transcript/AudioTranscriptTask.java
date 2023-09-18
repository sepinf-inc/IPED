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

    @Override
    public List<Configurable<?>> getConfigurables() {
        AudioTranscriptConfig result = ConfigurationManager.get().findObject(AudioTranscriptConfig.class);
        if(result == null) {
            result = new AudioTranscriptConfig();
        }
        return Arrays.asList(result);
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {
        AudioTranscriptConfig transcriptConfig = configurationManager.findObject(AudioTranscriptConfig.class);

        impl = (AbstractTranscriptTask) Class.forName(transcriptConfig.getClassName()).getDeclaredConstructor().newInstance();
        impl.setWorker(worker);
        impl.init(configurationManager);
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
