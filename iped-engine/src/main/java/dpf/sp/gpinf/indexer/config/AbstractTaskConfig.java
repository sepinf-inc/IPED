package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Path;

import macee.core.Configurable;
import macee.core.EnabledInterface;

public abstract class AbstractTaskConfig<T> implements Configurable<T>, EnabledInterface {
    
    protected EnableTaskProperty enabledProp;

    public abstract String getTaskEnableProperty();

    public abstract String getTaskConfigFileName();

    public abstract void processTaskConfig(Path resource) throws IOException;

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return new Filter<Path>() {
            @Override
            public boolean accept(Path entry) throws IOException {
                return entry.endsWith(getTaskConfigFileName()) || entry.endsWith(IPEDConfig.CONFIG_FILE);
            }
        };
    }

    @Override
    public void processConfig(Path resource) throws IOException {
        if (IPEDConfig.CONFIG_FILE.equals(resource.getFileName().toString())) {
            enabledProp = new EnableTaskProperty(getTaskEnableProperty());
            enabledProp.processConfig(resource);
        } else {
            processTaskConfig(resource);
        }
    }

    @Override
    public boolean isEnabled() {
        return enabledProp.isEnabled();
    }

    @Override
    public void setEnabled(boolean enabled) {
        enabledProp.setEnabled(enabled);
    }

}
