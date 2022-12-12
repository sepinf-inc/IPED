package iped.engine.config;

import java.io.File;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import iped.configuration.Configurable;
import iped.configuration.IConfigurationDirectory;
import iped.configuration.ObjectManager;

public class ProfileManager implements ObjectManager<IConfigurationDirectory>{
    private static ProfileManager singleton = null;
    Set<IConfigurationDirectory> listOfProfileDirectories = new TreeSet<IConfigurationDirectory>();
    private File profilesDir;
    private File confDir;


    public static ProfileManager get() {
        if(singleton==null) {
            synchronized (ConfigurationManager.class) {
                    singleton = new ProfileManager();
            }
        }
        return singleton;
    }

    /**
     * populate the listOfProfileDirectories with all profiles located on iped/profile directory
     */
    private ProfileManager() {
        confDir = Paths.get(System.getProperty(IConfigurationDirectory.IPED_APP_ROOT), Configuration.CONF_DIR ).toFile();
        profilesDir = Paths.get(System.getProperty(IConfigurationDirectory.IPED_APP_ROOT), Configuration.PROFILES_DIR ).toFile();
        for(File currentProfile : profilesDir.listFiles() ){
            ConfigurationDirectory currentProfileDirectory = new ConfigurationDirectory(confDir.toPath());
            currentProfileDirectory.addPath(currentProfile.toPath());
            currentProfileDirectory.setName(currentProfile.getName());
            listOfProfileDirectories.add(currentProfileDirectory);
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
        return listOfProfileDirectories;
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

        ConfigurationDirectory configDirectory = new ConfigurationDirectory(confDir.toPath());
        configDirectory.addPath(newProfile.toPath());
        configDirectory.setName(profileName);
        listOfProfileDirectories.add(configDirectory);

        return configDirectory;
    }
}
