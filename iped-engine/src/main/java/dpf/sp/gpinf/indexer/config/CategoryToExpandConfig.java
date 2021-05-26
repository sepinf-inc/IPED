package dpf.sp.gpinf.indexer.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class CategoryToExpandConfig extends AbstractTaskConfig<Set<String>> {

    public static final String CONFIG_FILE = "CategoriesToExpand.txt";
    private static final String ENABLED = "expandContainers";

    private Set<String> categoriesToExpand = new HashSet<String>();

    public boolean isToBeExpanded(Collection<String> categories) {

        if (!super.isEnabled()) {
            return false;
        }

        for (String category : categories) {
            if (categoriesToExpand.contains(category)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getTaskEnableProperty() {
        return ENABLED;
    }

    @Override
    public String getTaskConfigFileName() {
        return CONFIG_FILE;
    }

    @Override
    public void processTaskConfig(Path resource) throws IOException {
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

    @Override
    public Set<String> getConfiguration() {
        return categoriesToExpand;
    }

    @Override
    public void setConfiguration(Set<String> config) {
        categoriesToExpand = config;
    }

}
