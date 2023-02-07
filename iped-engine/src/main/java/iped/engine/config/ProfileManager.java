package iped.engine.config;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import iped.configuration.Configurable;
import iped.configuration.IConfigurationDirectory;
import iped.configuration.ObjectManager;

public class ProfileManager implements ObjectManager<IConfigurationDirectory>{
    public static final String PROFILE_EXTENSION = ".ipedprofile";
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
            if(currentProfile.isDirectory()) {
                ConfigurationDirectory currentProfileDirectory = new ConfigurationDirectory(confDir.toPath());
                currentProfileDirectory.addPath(currentProfile.toPath());
                currentProfileDirectory.setName(currentProfile.getName());
                listOfProfileDirectories.add(currentProfileDirectory);
            }else {
                if(currentProfile.getName().endsWith(PROFILE_EXTENSION)) {
                    SerializedConfigurationDirectory currentProfileDirectory = new SerializedConfigurationDirectory(currentProfile.toPath());
                    currentProfileDirectory.setName(currentProfile.getName().substring(0,currentProfile.getName().lastIndexOf(PROFILE_EXTENSION)));
                    listOfProfileDirectories.add(currentProfileDirectory);
                }
            }
        }
    }

    public ConfigurationDirectory getDefaultProfile() {
        confDir = Paths.get(System.getProperty(IConfigurationDirectory.IPED_APP_ROOT), Configuration.CONF_DIR ).toFile();
        ConfigurationDirectory currentProfileDirectory = new ConfigurationDirectory(confDir.toPath());
        currentProfileDirectory.setName("default");
        return currentProfileDirectory;
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
        listOfProfileDirectories.add(aObject);
    }

    @Override
    public void removeObject(IConfigurationDirectory profileDirectory) {
        File toDeleteProfile = new File(profilesDir, profileDirectory.getName()+PROFILE_EXTENSION);
        if(toDeleteProfile.exists()) {
            toDeleteProfile.delete();
        }
        listOfProfileDirectories.remove(profileDirectory);
    }

    public IConfigurationDirectory createProfilePath(String profileName, ConfigurationManager configurationManager) throws FileAlreadyExistsException {
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

    public IConfigurationDirectory createProfile(String profileName, ConfigurationManager configurationManager) throws FileAlreadyExistsException {
        File newProfile = new File(profilesDir, profileName + PROFILE_EXTENSION);
        if(newProfile.exists()) {
            throw new FileAlreadyExistsException("Profile name already exists");
        }

        try {
            ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(newProfile));
            Set<Configurable<?>> configs = configurationManager.getObjects();
            for (Iterator iterator = configs.iterator(); iterator.hasNext();) {
                Configurable<?> configurable = (Configurable<?>) iterator.next();
                oos.writeObject(configurable);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        
        SerializedConfigurationDirectory configDirectory = new SerializedConfigurationDirectory(newProfile.toPath());
        configDirectory.setName(profileName);
        listOfProfileDirectories.add(configDirectory);

        return configDirectory;
    }
}
