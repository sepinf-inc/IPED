package dpf.sp.gpinf.indexer.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MediaTypeRegistry;

public class CategoryConfig extends AbstractTaskConfig<Map<String, String>> {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private static final String CONFIG_FILE = "CategoriesByTypeConfig.txt";

    private transient MediaTypeRegistry registry = TikaConfig.getDefaultConfig().getMediaTypeRegistry();

    private Map<String, String> mimetypeToCategoryMap = new HashMap<>();

    public String getCategory(MediaType type) {

        String category;
        do {
            category = mimetypeToCategoryMap.get(type.toString());
            if (category == null) {
                category = mimetypeToCategoryMap.get(type.getType());
            }
            if (category != null) {
                return category;
            }

            type = registry.getSupertype(type);

        } while (type != null);

        return ""; //$NON-NLS-1$
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public Map<String, String> getConfiguration() {
        return mimetypeToCategoryMap;
    }

    @Override
    public void setConfiguration(Map<String, String> config) {
        this.mimetypeToCategoryMap = config;
    }

    @Override
    public String getTaskEnableProperty() {
        return "";
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
                if (line.startsWith("#")) { //$NON-NLS-1$
                    continue;
                }
                String[] keyValuePair = line.split("="); //$NON-NLS-1$
                if (keyValuePair.length == 2) {
                    String category = keyValuePair[0].trim();
                    String mimeTypes = keyValuePair[1].trim();
                    for (String mimeType : mimeTypes.split(";")) { //$NON-NLS-1$
                        mimeType = mimeType.trim();
                        MediaType mt = MediaType.parse(mimeType);
                        if (mt != null) {
                            mimeType = registry.normalize(mt).toString();
                        }
                        mimetypeToCategoryMap.put(mimeType, category);
                    }
                }
            }
        }
    }

}
