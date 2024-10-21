package iped.engine.config;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import iped.configuration.Configurable;
import iped.configuration.IConfigurationDirectory;
import iped.configuration.ObjectManager;

public class ConfigurationManager implements ObjectManager<Configurable<?>> {
    private static ConfigurationManager selectedCM = null;
    List<ConfigurableChangeListener> configurableChangeListeners = new ArrayList<ConfigurableChangeListener>();

    private IConfigurationDirectory directory;
    
    class ConfigurableWrapper{
        Configurable<?> c;

        public ConfigurableWrapper(Configurable<?> config) {
            this.c = config;
        }

        @Override
        public int hashCode() {
            if(c instanceof EnableTaskProperty) {
                return Objects.hash(c.getClass(), ((EnableTaskProperty)c).getPropertyName());
            }
            return c.getClass().hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if(c instanceof EnableTaskProperty) {
                return c.getClass().equals(((ConfigurableWrapper)o).c.getClass()) && ((EnableTaskProperty) c).getPropertyName().equals(((EnableTaskProperty) ((ConfigurableWrapper)o).c).getPropertyName());
            }
            if(c instanceof DefaultTaskPropertiesConfig) {
                return c.getClass().equals(((ConfigurableWrapper)o).c.getClass()) && ((DefaultTaskPropertiesConfig) c).getTaskConfigFileName().equals(((DefaultTaskPropertiesConfig) ((ConfigurableWrapper)o).c).getTaskConfigFileName());
            }
            return c.getClass().equals(((ConfigurableWrapper)o).c.getClass());
        }
        
    }

    private Map<ConfigurableWrapper, Boolean> loadedConfigurablesState = new LinkedHashMap<>();

    boolean changed=false;

    /**
     * @return returns current selected configuration profile
     */
    public static ConfigurationManager get() {
        return selectedCM;
    }

    public static void setCurrentConfigurationManager(ConfigurationManager cm) {
        selectedCM = cm;
    }

    /**
     * Creates an ConfigurationManager instance an sets it as the current selected CM.
     */
    public static ConfigurationManager createInstance(IConfigurationDirectory directory) {
        if (selectedCM == null) {
            synchronized (ConfigurationManager.class) {
                if (selectedCM == null) {
                    selectedCM = new ConfigurationManager(directory);
                }
            }
        }
        return selectedCM;
    }

    public ConfigurationManager(IConfigurationDirectory directory) {
        this.directory = directory;
    }

    @Override
    public void addObject(Configurable<?> config) {
        loadedConfigurablesState.put(new ConfigurableWrapper(config), false);
    }

    public void loadConfigs() throws IOException {
        ConfigurableWrapper[] array = loadedConfigurablesState.keySet().toArray(new ConfigurableWrapper[0]);
        for (int i=0; i< array.length; i++) {
            Configurable<?> configurable = array[i].c;
            loadConfig(configurable);
        }
    }

    public void loadConfig(Configurable<?> configurable, boolean forceReload) throws IOException {
        Boolean loaded = loadedConfigurablesState.get(new ConfigurableWrapper(configurable)); 
        if (forceReload || loaded ==null || loaded == false) {
            List<Path> resources = directory.lookUpResource(configurable);
            if(resources!=null) {
                configurable.processConfigs(resources);
            }
            ConfigurableWrapper cw = new ConfigurableWrapper(configurable);
            loadedConfigurablesState.remove(cw);
            loadedConfigurablesState.put(cw , true);
        }       
    }

    public void loadConfig(Configurable<?> configurable) throws IOException {
        loadConfig(configurable,false);
    }

    public void loadConfigs(boolean forceReload) throws IOException {
        if(directory instanceof ConfigurationDirectory) {
            for (Iterator<ConfigurableWrapper> iterator = loadedConfigurablesState.keySet().iterator(); iterator.hasNext();) {
                Configurable<?> configurable = iterator.next().c;
                if(forceReload) {
                    configurable.reset();
                }

                List<Path> resources = directory.lookUpResource(configurable);

                configurable.processConfigs(resources);
            }
        }else if(directory instanceof SerializedConfigurationDirectory) {
            ObjectInputStream ois = ((SerializedConfigurationDirectory) directory).openInputStream();
            try {
                Configurable configurable=(Configurable)ois.readObject();
                while(configurable!=null) {
                    ConfigurableWrapper cw = new ConfigurableWrapper(configurable);
                    loadedConfigurablesState.remove(cw);
                    loadedConfigurablesState.put(cw, true);
                    try {
                        configurable=(Configurable)ois.readObject();
                    }catch (EOFException e) {
                        configurable=null;
                    }
                }
            } catch (ClassNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }finally {
                ois.close();
            }
        }
    }

    public IConfigurationDirectory getConfigurationDirectory() {
        return directory;
    }

    @Override
    public Set<Configurable<?>> findObjects(Class<? extends Configurable<?>> clazz) {
        Set<Configurable<?>> result = new HashSet<>();

        for (Iterator<ConfigurableWrapper> iterator = loadedConfigurablesState.keySet().iterator(); iterator.hasNext();) {
            Configurable<?> configurable = iterator.next().c;
            if (clazz.isInstance(configurable)) {
                result.add(configurable);
            }
        }

        return result;
    }

    public <T extends Configurable<?>> T findObject(Class<? extends Configurable<?>> clazz) {
        for (ConfigurableWrapper configurablew : loadedConfigurablesState.keySet()) {
            if (configurablew.c.getClass().equals(clazz)) {
                return (T) configurablew.c;
            }
        }
        return null;
    }

    public AbstractTaskConfig<?> getTaskConfigurable(String configFileName) {
        for (ConfigurableWrapper configw : loadedConfigurablesState.keySet()) {
            if (configw.c instanceof AbstractTaskConfig) {
                AbstractTaskConfig<?> taskConfig = (AbstractTaskConfig<?>) configw.c;
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
        EnableTaskProperty enableProp = getEnableTaskConfigurable(propertyName);
        if (enableProp != null) {
            return enableProp.isEnabled();
        } else {
            return false;
        }
    }

    public EnableTaskProperty getEnableTaskPropertyObject(String propertyName) {
        return getEnableTaskConfigurable(propertyName);
    }

    @Override
    public Set<Configurable<?>> findObjects(String className) {
        Set<Configurable<?>> result = new HashSet<>();

        for (Iterator<ConfigurableWrapper> iterator = loadedConfigurablesState.keySet().iterator(); iterator.hasNext();) {
            Configurable<?> configurable = iterator.next().c;
            if (configurable.getClass().getName().equals(className)) {
                result.add(configurable);
            }
        }

        return result;
    }

    @Override
    public Set<Configurable<?>> getObjects() {
        LinkedHashSet<Configurable<?>> result = new LinkedHashSet<Configurable<?>>();
        for (Iterator iterator = loadedConfigurablesState.keySet().iterator(); iterator.hasNext();) {
            ConfigurableWrapper configurablew = (ConfigurableWrapper) iterator.next();
            result.add(configurablew.c);
            
        }
        return result;
    }

    @Override
    public void removeObject(Configurable<?> aObject) {
        loadedConfigurablesState.remove(new ConfigurableWrapper(aObject));
    }

    public void saveSerializedConfig(File file) throws FileNotFoundException, IOException {
        try(FileOutputStream fos = new FileOutputStream(file);
                ObjectOutputStream oos = new ObjectOutputStream(fos)){
            oos.writeObject(loadedConfigurablesState);
        }
    }

    @SuppressWarnings("unchecked")
    public void loadSerializedConfig(File file)
            throws FileNotFoundException, IOException, ClassNotFoundException {
        try (FileInputStream fis = new FileInputStream(file); ObjectInputStream ois = new ObjectInputStream(fis)) {
            loadedConfigurablesState = (Map<ConfigurableWrapper, Boolean>) ois.readObject();
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

    /**
     * A method to refresh the changed configurable properties values
     * First whe change the old instance on loadedConfigurables by the new one
     * so whe call LoadConfig method to reload the new propertie
     * @param clazz - Configurable Class
     */
    public void reloadConfigurable(Class<? extends Configurable<?>> clazz){
        try {
            removeObject(get().findObject(clazz));
            Class<?>[] empty = {};
            Configurable<?> configurable = clazz.getConstructor(empty).newInstance();
            addObject(configurable);
            loadConfig(configurable);
        } catch (IOException | NoSuchMethodException | InvocationTargetException | InstantiationException | IllegalAccessException ex) {
            ex.printStackTrace();
        }
    }

}