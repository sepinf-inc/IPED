package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;

import macee.core.EnabledInterface;

public class ExportByCategoriesConfig extends AbstractPropertiesConfigurable implements EnabledInterface {

    public static final String CONFIG_FILE = "CategoriesToExport.txt"; //$NON-NLS-1$
    
    private HashSet<String> categoriesToExport = new HashSet<String>();

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return new Filter<Path>() {
            @Override
            public boolean accept(Path entry) throws IOException {
                return entry.endsWith(CONFIG_FILE);
            }
        };
    }

    @Override
    public void processConfig(Path resource) throws IOException {
        List<String> lines = Files.readAllLines(resource);
        for (String line : lines) { // $NON-NLS-1$
            if (line.trim().startsWith("#") || line.trim().isEmpty()) { //$NON-NLS-1$
                continue;
            }
            categoriesToExport.add(line.trim());
        }
    }

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

}
