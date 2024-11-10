package iped.engine.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ExportByCategoriesConfig extends AbstractTaskConfig<Set<String>> {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    public static final String CONFIG_FILE = "CategoriesToExport.txt"; //$NON-NLS-1$
    public static final String ENABLE_PARAM = "enableAutomaticExportFiles";
    
    private Set<String> categoriesToExport = new HashSet<String>();

    public boolean hasCategoryToExport() {
        return categoriesToExport.size() > 0;
    }

    public boolean isToExportCategory(String category) {
        return categoriesToExport.contains(category);
    }

    @Override
    public boolean isEnabled() {
        return hasCategoryToExport();
    }

    @Override
    public Set<String> getConfiguration() {
        return categoriesToExport;
    }

    @Override
    public void setConfiguration(Set<String> config) {
        this.categoriesToExport = config;
    }

    @Override
    public String getTaskEnableProperty() {
        return ENABLE_PARAM;
    }

    @Override
    public String getTaskConfigFileName() {
        return CONFIG_FILE;
    }

    @Override
    public void processTaskConfig(Path resource) throws IOException {
        List<String> lines = Files.readAllLines(resource);
        for (String line : lines) { // $NON-NLS-1$
            if (line.trim().startsWith("#") || line.trim().isEmpty()) { //$NON-NLS-1$
                continue;
            }
            categoriesToExport.add(line.trim());
        }
    }

    @Override
    public void save(Path resource) {
        try {
            File confDir = new File(resource.toFile(), Configuration.CONF_DIR);
            confDir.mkdirs();
            File confFile = new File(confDir, CONFIG_FILE);            
            Files.write(confFile.toPath(),categoriesToExport);
        }catch (Exception e) {
            e.printStackTrace();
        }
    }

}
