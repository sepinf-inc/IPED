package iped.engine.config;

import java.io.File;
import java.nio.file.FileAlreadyExistsException;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import iped.configuration.Configurable;
import iped.configuration.IConfigurationDirectory;
import iped.configuration.ObjectManager;

public class ProfileManager implements ObjectManager<IConfigurationDirectory>{
    private static ProfileManager singleton = null;
    Set<IConfigurationDirectory> configDirectories = new TreeSet<IConfigurationDirectory>();
    private File profilesDir;
    private File appRoot;

    public static ProfileManager get() {
        if(singleton==null) {
            return createInstance();
        }
        return singleton;
    }

    public static ProfileManager createInstance() {
        if (singleton == null) {
            synchronized (ConfigurationManager.class) {
                if (singleton == null) {
                    singleton = new ProfileManager();
                }
            }
        }
        return singleton;
    }

    public ProfileManager() {
        Configuration globalConfig = Configuration.getInstance();

        appRoot = new File("/home/patrick.pdb/ipedtimeline-workspace/iped-parent/target/release/iped-4.1-snapshot/conf");
        profilesDir = new File("/home/patrick.pdb/ipedtimeline-workspace/iped-parent/target/release/iped-4.1-snapshot/profiles");

        File[] files = profilesDir.listFiles();
        if(files!=null) {
            for (int i = 0; i < files.length; i++) {
                ConfigurationDirectory configDirectory = new ConfigurationDirectory(appRoot.toPath());
                configDirectory.addPath(files[i].toPath());
                configDirectory.setName(files[i].getName());
                configDirectories.add(configDirectory);
            }        
        }
    }

    @Override
    public Set<? extends IConfigurationDirectory> findObjects(Class<? extends IConfigurationDirectory> clazz) {
        return null;
    }

    @Override
    public Set<IConfigurationDirectory> findObjects(String className) {
        return null;
    }

    @Override
    public Set<IConfigurationDirectory> getObjects() {
        return configDirectories;
    }

    @Override
    public void addObject(IConfigurationDirectory aObject) {
        
    }

    @Override
    public void removeObject(IConfigurationDirectory aObject) {
        
    }

    public IConfigurationDirectory createProfile(String profileName, ConfigurationManager configurationManager) throws FileAlreadyExistsException {
        File newProfile = new File(profilesDir, profileName);
        if(newProfile.exists()) {
            throw new FileAlreadyExistsException("Profile name already exists");
        }

        newProfile.mkdir();

        Set<Configurable<?>> configs = configurationManager.getObjects();
        for (Iterator iterator = configs.iterator(); iterator.hasNext();) {
            Configurable<?> configurable = (Configurable<?>) iterator.next();
            configurable.save(newProfile.toPath());
        }

        ConfigurationDirectory configDirectory = new ConfigurationDirectory(appRoot.toPath());
        configDirectory.addPath(newProfile.toPath());
        configDirectory.setName(profileName);
        configDirectories.add(configDirectory);
        
        return configDirectory;
    }
}
