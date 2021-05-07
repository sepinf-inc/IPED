package iped3.configuration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

import macee.core.Configurable;

public interface IConfigurationDirectory {

    public static final String IPED_CONF_PATH = "iped.configPath";

    public static final String IPED_ROOT = "iped.root";

    public List<Path> getResourceLookupFolders();

    public List<Path> lookUpResource(Predicate<Path> predicate) throws IOException;

    public List<Path> lookUpResource(Configurable configurable) throws IOException;
}
