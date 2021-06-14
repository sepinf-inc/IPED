package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import iped3.configuration.IConfigurationDirectory;
import macee.core.Configurable;
import macee.core.ObjectManager;

public class ConfigurationManager implements ObjectManager<Configurable<?>> {

    private static ConfigurationManager singleton = null;

    IConfigurationDirectory directory;
    HashMap<Configurable<?>, Boolean> loadedConfigurables = new LinkedHashMap<>();

    public static ConfigurationManager getInstance() {
        return singleton;
    }

    public static ConfigurationManager createInstance(IConfigurationDirectory directory) {
        if (singleton == null) {
            synchronized (ConfigurationManager.class) {
                if (singleton == null) {
                    singleton = new ConfigurationManager(directory);
                }
            }
        }
        return singleton;
    }

    private ConfigurationManager(IConfigurationDirectory directory) {
        this.directory = directory;
    }

    @Override
    public void addObject(Configurable<?> config) {
        loadedConfigurables.put(config, false);
    }

    public void loadConfigs() throws IOException {
        for (Iterator<Configurable<?>> iterator = loadedConfigurables.keySet().iterator(); iterator.hasNext();) {
            Configurable<?> configurable = iterator.next();
            loadConfig(configurable);
        }
    }

    public void loadConfig(Configurable<?> configurable) throws IOException {
        if (loadedConfigurables.get(configurable) == false) {
            List<Path> resources = directory.lookUpResource(configurable);
            configurable.processConfigs(resources);
            loadedConfigurables.put(configurable, true);
        }
    }

    public void loadConfigs(boolean forceReload) throws IOException {
        for (Iterator<Configurable<?>> iterator = loadedConfigurables.keySet().iterator(); iterator.hasNext();) {
            Configurable<?> configurable = iterator.next();

            List<Path> resources = directory.lookUpResource(configurable);

            configurable.processConfigs(resources);
        }
    }

    public IConfigurationDirectory getConfigurationDirectory() {
        return directory;
    }

    @Override
    public Set<Configurable<?>> findObjects(Class<? extends Configurable<?>> clazz) {
        Set<Configurable<?>> result = new HashSet<>();

        for (Iterator<Configurable<?>> iterator = loadedConfigurables.keySet().iterator(); iterator.hasNext();) {
            Configurable<?> configurable = iterator.next();
            if (configurable.getClass().equals(clazz)) {
                result.add(configurable);
            }
        }

        return result;
    }

    public static <T extends Configurable<?>> T findObject(Class<? extends Configurable<?>> clazz) {
        for (Configurable<?> configurable : singleton.loadedConfigurables.keySet()) {
            if (configurable.getClass().equals(clazz)) {
                return (T) configurable;
            }
        }
        return null;
    }

    public AbstractTaskConfig<?> getTaskConfigurable(String configFileName) {
        for (Configurable<?> config : singleton.loadedConfigurables.keySet()) {
            if (config instanceof AbstractTaskConfig) {
                AbstractTaskConfig<?> taskConfig = (AbstractTaskConfig<?>) config;
                if (taskConfig.getTaskConfigFileName().equals(configFileName)) {
                    return taskConfig;
                }
            }
        }
        return null;
    }

    public EnableTaskProperty getEnableTaskConfigurable(String propertyName) {
        Set<Configurable<?>> configs = findObjects(EnableTaskProperty.class);
        for(Configurable<?> config : configs) {
            EnableTaskProperty enableProp = (EnableTaskProperty) config;
            if (enableProp.getPropertyName().equals(propertyName)) {
                return enableProp;
            }
        }
        return null;
    }

    public static boolean getEnableTaskProperty(String propertyName) {
        EnableTaskProperty enableProp = singleton.getEnableTaskConfigurable(propertyName);
        if (enableProp != null) {
            return enableProp.isEnabled();
        } else {
            return false;
        }
    }

    @Override
    public Set<Configurable<?>> findObjects(String className) {
        Set<Configurable<?>> result = new HashSet<>();

        for (Iterator<Configurable<?>> iterator = loadedConfigurables.keySet().iterator(); iterator.hasNext();) {
            Configurable<?> configurable = iterator.next();
            if (configurable.getClass().getName().equals(className)) {
                result.add(configurable);
            }
        }

        return result;
    }

    @Override
    public Set<Configurable<?>> getObjects() {
        return loadedConfigurables.keySet();
    }

    @Override
    public void removeObject(Configurable<?> aObject) {
        loadedConfigurables.remove(aObject);
    }

}