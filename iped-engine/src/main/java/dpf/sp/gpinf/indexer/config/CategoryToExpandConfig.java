package dpf.sp.gpinf.indexer.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;

public class CategoryToExpandConfig extends AbstractPropertiesConfigurable {

    public static final String CONFIG_FILE = "CategoriesToExpand.txt";
    private static final String ENABLED = "expandContainers";

    private boolean expandContainers;
    private HashSet<String> categoriesToExpand = new HashSet<String>();

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return new Filter<Path>() {
            @Override
            public boolean accept(Path entry) throws IOException {
                return entry.endsWith(CONFIG_FILE) || entry.endsWith(IPEDConfig.CONFIG_FILE);
            }
        };
    }

    @Override
    public void processConfig(Path resource) throws IOException {
        if (resource.getFileName().toString().equals(IPEDConfig.CONFIG_FILE)) {
            properties.load(resource.toFile());
            expandContainers = Boolean.valueOf(properties.getProperty(ENABLED));
        } else {
            try (BufferedReader reader = Files.newBufferedReader(resource)) {
                String line = reader.readLine();
                while ((line = reader.readLine()) != null) {
                    if (line.trim().startsWith("#") || line.trim().isEmpty()) { //$NON-NLS-1$
                        continue;
                    }
                    categoriesToExpand.add(line.trim());
                }
            }
        }

    }

    public boolean isToBeExpanded(Collection<String> categories) {

        if (!expandContainers) {
            return false;
        }

        for (String category : categories) {
            if (categoriesToExpand.contains(category)) {
                return true;
            }
        }
        return false;
    }

    public void setExpandContainers(boolean value) {
        this.expandContainers = value;
    }

}
