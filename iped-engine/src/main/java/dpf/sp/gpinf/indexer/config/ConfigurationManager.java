package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import iped3.configuration.ConfigurationDirectory;
import macee.core.Configurable;
import macee.core.ObjectManager;

public class ConfigurationManager implements ObjectManager<Configurable> {
    static ConfigurationManager singleton = null;

    static public ConfigurationManager getInstance() {
        return singleton;
    }

    ConfigurationDirectory directory;
    HashMap<Configurable, Boolean> loadedConfigurables = new LinkedHashMap<Configurable, Boolean>();

    public ConfigurationManager(ConfigurationDirectory directory) {
        this.directory = directory;
        ConfigurationManager.singleton = this;
    }

    final HashMap<Object, Configurable> configurations = new HashMap<Object, Configurable>();

    public void addObject(Configurable config) {
        loadedConfigurables.put(config, false);
    }

    public void loadConfigs() throws IOException {
        for (Iterator<Configurable> iterator = loadedConfigurables.keySet().iterator(); iterator.hasNext();) {
            Configurable configurable = iterator.next();
            if (loadedConfigurables.get(configurable) == false) {
                List<Path> resources = directory.lookUpResource(configurable);

                configurable.processConfigs(resources);
                loadedConfigurables.put(configurable, new Boolean(true));
            }
        }
    }

    public void loadConfigs(boolean forceReload) throws IOException {
        for (Iterator<Configurable> iterator = loadedConfigurables.keySet().iterator(); iterator.hasNext();) {
            Configurable configurable = iterator.next();

            List<Path> resources = directory.lookUpResource(configurable);

            configurable.processConfigs(resources);
        }
    }

    public ConfigurationDirectory getConfigurationDirectory() {
        return directory;
    }

    @Override
    public Set<Configurable> findObjects(Class<? extends Configurable> clazz) {
        Set<Configurable> result = new HashSet<Configurable>();

        for (Iterator<Configurable> iterator = loadedConfigurables.keySet().iterator(); iterator.hasNext();) {
            Configurable configurable = iterator.next();
            if (configurable.getClass().equals(clazz)) {
                result.add(configurable);
            }
        }

        return result;
    }

    @Override
    public Set<Configurable> findObjects(String className) {
        Set<Configurable> result = new HashSet<Configurable>();

        for (Iterator<Configurable> iterator = loadedConfigurables.keySet().iterator(); iterator.hasNext();) {
            Configurable configurable = iterator.next();
            if (configurable.getClass().getName().equals(className)) {
                result.add(configurable);
            }
        }

        return result;
    }

    @Override
    public Set<Configurable> getObjects() {
        return loadedConfigurables.keySet();
    }

    @Override
    public void removeObject(Configurable aObject) {
        loadedConfigurables.remove(aObject);
    }

}