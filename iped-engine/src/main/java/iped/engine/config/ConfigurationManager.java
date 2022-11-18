package iped.engine.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import iped.configuration.Configurable;
import iped.configuration.IConfigurationDirectory;
import iped.configuration.ObjectManager;

public class ConfigurationManager implements ObjectManager<Configurable<?>> {
    private static ConfigurationManager singleton = null;
    List<ConfigurableChangeListener> configurableChangeListeners = new ArrayList<ConfigurableChangeListener>();   

    private IConfigurationDirectory directory;

    private Map<Configurable<?>, Boolean> loadedConfigurables = new LinkedHashMap<>();
    private List<Configurable<?>> changedConfigurables = new ArrayList<Configurable<?>>();

    boolean changed=false;

    public static ConfigurationManager get() {
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
    
    public ConfigurationManager(IConfigurationDirectory directory) {
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

    public <T extends Configurable<?>> T findObject(Class<? extends Configurable<?>> clazz) {
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

    public boolean getEnableTaskProperty(String propertyName) {
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

    public void saveSerializedConfig(File file) throws FileNotFoundException, IOException {
        try(FileOutputStream fos = new FileOutputStream(file);
                ObjectOutputStream oos = new ObjectOutputStream(fos)){
            oos.writeObject(loadedConfigurables);
        }
    }

    @SuppressWarnings("unchecked")
    public void loadSerializedConfig(File file)
            throws FileNotFoundException, IOException, ClassNotFoundException {
        try (FileInputStream fis = new FileInputStream(file); ObjectInputStream ois = new ObjectInputStream(fis)) {
            loadedConfigurables = (Map<Configurable<?>, Boolean>) ois.readObject();
        }
    }
/*
    public void saveConfigurables() {
        for (Iterator<Configurable<?>> iterator = changedConfigurable.iterator(); iterator.hasNext();) {
            Configurable<?> config = iterator.next();
            
            try {
                Writer w = null;
                try {
                    List<Path> resources = directory.lookUpResource(config);
                    for (Iterator iterator2 = resources.iterator(); iterator2.hasNext();) {
                        Path path = (Path) iterator2.next();
                        config.save(path);                            
                    }
                }finally {
                    if(w!=null) {
                        w.close();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
*/
    
    public IConfigurationDirectory getDirectory() {
        return directory;
    }

    public void setDirectory(IConfigurationDirectory directory) {
        this.directory = directory;
    }

    public void notifyUpdate(Configurable<?> configurable) {
        changedConfigurables.add(configurable);
        for (Iterator<ConfigurableChangeListener> iterator = configurableChangeListeners.iterator(); iterator.hasNext();) {
            ConfigurableChangeListener ccl =  iterator.next();
            ccl.onChange(configurable);            
        }        
        changed=true;
    }
    
    public boolean hasChanged() {
        return changed;
    }

    public void addConfigurableChangeListener(ConfigurableChangeListener ccl) {
        configurableChangeListeners.add(ccl);
    }

    public void removeConfigurableChangeListener(ConfigurableChangeListener ccl) {
        configurableChangeListeners.remove(ccl);
    }
}