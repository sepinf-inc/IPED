package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class NamedEntityTaskConfig extends AbstractTaskPropertiesConfig {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public static final String CONF_FILE = "NamedEntityRecognitionConfig.txt"; //$NON-NLS-1$

    private static final String ENABLE_PARAM = "enableNamedEntityRecogniton"; //$NON-NLS-1$

    private String nerImpl;

    private float minLangScore = 0;

    private Map<String, String> langToModelMap = new HashMap<>();

    private Set<String> mimeTypesToIgnore = new HashSet<String>();

    private Set<String> categoriesToIgnore = new HashSet<String>();

    public String getNerImpl() {
        return this.nerImpl;
    }

    public float getMinLangScore() {
        return minLangScore;
    }

    public Map<String, String> getLangToModelMap() {
        return langToModelMap;
    }

    public Set<String> getMimeTypesToIgnore() {
        return mimeTypesToIgnore;
    }

    public Set<String> getCategoriesToIgnore() {
        return categoriesToIgnore;
    }

    @Override
    public String getTaskEnableProperty() {
        return ENABLE_PARAM;
    }

    @Override
    public String getTaskConfigFileName() {
        return CONF_FILE;
    }

    @Override
    public void processTaskConfig(Path resource) throws IOException {

        properties.load(resource.toFile());

        nerImpl = properties.getProperty("NERImpl"); //$NON-NLS-1$

        String langAndModel;
        int i = 0;
        while ((langAndModel = properties.getProperty("langModel_" + i++)) != null) { //$NON-NLS-1$
            String[] strs = langAndModel.split(":"); //$NON-NLS-1$
            String lang = strs[0].trim();
            String modelPath = strs[1].trim();
            langToModelMap.put(lang, modelPath);
        }

        String mimes = properties.getProperty("mimeTypesToIgnore"); //$NON-NLS-1$
        for (String mime : mimes.split(";")) { //$NON-NLS-1$
            mimeTypesToIgnore.add(mime.trim());
        }

        String categories = properties.getProperty("categoriesToIgnore"); //$NON-NLS-1$
        for (String cat : categories.split(";")) { //$NON-NLS-1$
            categoriesToIgnore.add(cat.trim());
        }

        minLangScore = Float.valueOf(properties.getProperty("minLangScore").trim()); //$NON-NLS-1$

    }

}
