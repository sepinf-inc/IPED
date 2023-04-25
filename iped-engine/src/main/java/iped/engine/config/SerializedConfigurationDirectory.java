package iped.engine.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

import iped.configuration.Configurable;
import iped.configuration.IConfigurationDirectory;

public class SerializedConfigurationDirectory implements IConfigurationDirectory, Comparable<IConfigurationDirectory> {
    Path file=null;
    String name;

    public SerializedConfigurationDirectory(Path file) {
        this.file = file;
    }

    @Override
    public List<Path> getResourceLookupFolders() {
        return null;
    }

    @Override
    public List<Path> lookUpResource(Predicate<Path> predicate) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<Path> lookUpResource(Configurable<?> configurable) throws IOException {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String toString() {
        return name;
    }

    @Override
    public int compareTo(IConfigurationDirectory o) {
        return this.getName().compareTo(o.getName());
    }

    public ObjectInputStream openInputStream() throws IOException {
        FileInputStream fi = new FileInputStream(file.toFile());        
        return new ObjectInputStream(fi);
    }

    @Override
    public void addPath(Path path) {
        // TODO Auto-generated method stub
    }
}
