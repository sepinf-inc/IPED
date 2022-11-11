package iped.engine.config;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import iped.engine.data.Category;

public class CategoryToExpandConfig extends AbstractTaskConfig<Set<String>> {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    public static final String CONFIG_FILE = "CategoriesToExpand.txt";
    private static final String ENABLED = "expandContainers";

    private Set<String> categoriesToExpand = new HashSet<String>();
    CategoryConfig categoryConfig = null;

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
            if (categoryConfig == null) {
                categoryConfig = ConfigurationManager.get().findObject(CategoryConfig.class);
            }
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("#") || line.trim().isEmpty()) { //$NON-NLS-1$
                    continue;
                }

                LinkedList <Category> cats = new LinkedList<>();
                cats.push(categoryConfig.getCategoryFromName(line.trim()));
                while (cats.size() > 0) {
                    Category cat = cats.pop();
                    categoriesToExpand.add(cat.getName());
                    cats.addAll(cat.getChildren());
                }
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

    @Override
    public void save(Path resource) {
        try {
            File confDir = new File(resource.toFile(), Configuration.CONF_DIR);
            confDir.mkdirs();
            File confFile = new File(confDir, CONFIG_FILE);            
            Files.write(confFile.toPath(),categoriesToExpand);
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

}
