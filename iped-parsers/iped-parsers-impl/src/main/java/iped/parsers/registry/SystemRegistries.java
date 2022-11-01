package iped.parsers.registry;

import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;

import iped.parsers.registry.model.RegistryFile;

/*
 * Class used to represent a repository of registry files found in the case processing.
 */
public class SystemRegistries {
    HashMap<String, RegistryFile> system = new HashMap<String, RegistryFile>();
    HashMap<String, RegistryFile> software = new HashMap<String, RegistryFile>();
    HashMap<String, RegistryFile> sam = new HashMap<String, RegistryFile>();
    HashMap<String, RegistryFile> security = new HashMap<String, RegistryFile>();

    HashMap<String, RegistryFile> ntUserDat = new HashMap<String, RegistryFile>();

    private RegistryFile findNearestRF(String path, HashMap<String, RegistryFile> map) {
        if (map != null) {
            int i = path.lastIndexOf("/");
            boolean found = false;
            while (!found && i > 0) {
                path = path.substring(0, i);
                for (Entry<String, RegistryFile> entry : map.entrySet()) {
                    if (entry.getKey().startsWith(path)) {
                        return entry.getValue();
                    }
                }
                i = path.lastIndexOf("/");
            }
        }

        return null;
    }

    public RegistryFile getSystem(String path) {
        return findNearestRF(path, system);
    }

    public void addSystem(String path, RegistryFile system) {
        this.system.put(path, system);
    }

    public RegistryFile getSoftware(String path) {
        return findNearestRF(path, software);
    }

    public void addSoftware(String path, RegistryFile software) {
        this.software.put(path, software);
    }

    public RegistryFile getSam(String path) {
        return findNearestRF(path, sam);
    }

    public void addSam(String path, RegistryFile sam) {
        this.sam.put(path, sam);
    }

    public RegistryFile getSecurity(String path) {
        return findNearestRF(path, security);
    }

    public void addSecurity(String path, RegistryFile security) {
        this.security.put(path, security);
    }

    public void addNtUserDat(String path, RegistryFile rf) {
        ntUserDat.put(path, rf);
    }

    public RegistryFile getNtUser(String path) {
        return findNearestRF(path, ntUserDat);
    }

    public void addNtUserDat(String path) {
        ntUserDat.get(path);
    }

    public Set<String> getNtUserDats() {
        return ntUserDat.keySet();
    }
}
