package iped3.configuration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

import macee.core.Configurable;

public interface IConfigurationDirectory {
    public List<Path> getResourceLookupFolders();

    public List<Path> lookUpResource(Predicate<Path> predicate) throws IOException;

    public List<Path> lookUpResource(Configurable configurable) throws IOException;
}
