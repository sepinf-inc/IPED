package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import dpf.sp.gpinf.indexer.util.Util;

public class ExportByKeywordsConfig extends AbstractTaskConfig<List<String>> {

    private static final String CONFIG_FILE = "KeywordsToExport.txt";

    private List<String> keywords = new ArrayList<>();

    @Override
    public boolean isEnabled() {
        return !this.keywords.isEmpty();
    }

    public List<String> getKeywords() {
        return this.keywords;
    }

    @Override
    public void processTaskConfig(Path resource) throws IOException {

        String content = Util.readUTF8Content(resource.toFile());
        for (String line : content.split("\n")) { //$NON-NLS-1$
            line = line.trim();
            if (line.startsWith("#") || line.isEmpty()) { //$NON-NLS-1$
                continue;
            }
            keywords.add(line);
        }

    }

    @Override
    public List<String> getConfiguration() {
        return keywords;
    }

    @Override
    public void setConfiguration(List<String> config) {
        this.keywords = config;
    }

    @Override
    public String getTaskEnableProperty() {
        return ExportByCategoriesConfig.ENABLE_PARAM;
    }

    @Override
    public String getTaskConfigFileName() {
        return CONFIG_FILE;
    }

}
