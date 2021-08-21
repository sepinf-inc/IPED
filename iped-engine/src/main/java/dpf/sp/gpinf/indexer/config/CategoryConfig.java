package dpf.sp.gpinf.indexer.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.apache.tika.mime.MediaType;

import iped3.util.MediaTypes;

public class CategoryConfig extends AbstractTaskConfig<Map<String, String>> {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private static final String CONFIG_FILE = "CategoriesByTypeConfig.txt";

    private Map<String, String> mimetypeToCategoryMap = new HashMap<>();

    private transient Map<String, String> normalizedMap;

    private Map<String, String> getNormalizedMap() {
        if (normalizedMap == null) {
            synchronized (this) {
                if (normalizedMap == null) {
                    normalizedMap = new HashMap<>();
                    for(String key : mimetypeToCategoryMap.keySet()) {
                        MediaType type = MediaTypes.normalize(MediaType.parse(key));
                        String mime = type != null ? type.toString() : key;
                        normalizedMap.put(mime, mimetypeToCategoryMap.get(key));
                    }
                }
            }
        }
        return normalizedMap;
    }

    public String getCategory(MediaType type) {

        do {
            String category = getNormalizedMap().get(type.toString());
            if (category == null) {
                category = getNormalizedMap().get(type.getType());
            }
            if (category != null) {
                return category;
            }

            type = MediaTypes.getParentType(type);

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
                        mimetypeToCategoryMap.put(mimeType, category);
                    }
                }
            }
        }
    }

}
