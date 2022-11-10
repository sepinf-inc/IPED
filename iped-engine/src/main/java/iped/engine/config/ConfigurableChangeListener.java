package iped.engine.config;

import iped.configuration.Configurable;

public interface ConfigurableChangeListener {
    
    void onChange(Configurable<?> configurable);


}
