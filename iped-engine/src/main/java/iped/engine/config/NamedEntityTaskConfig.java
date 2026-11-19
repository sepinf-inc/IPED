package iped.engine.config;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import iped.utils.UTF8Properties;

public class NamedEntityTaskConfig extends AbstractTaskPropertiesConfig {

    private static final long serialVersionUID = 1L;

    public static final String CONF_FILE = "NamedEntityRecognitionConfig.txt"; //$NON-NLS-1$

    public static final String ENABLE_PARAM = "enableNamedEntityRecogniton"; //$NON-NLS-1$
    public static final String ENABLE_PARAM_CORRECT = "enableNamedEntityRecognition"; //$NON-NLS-1$

    private String nerImpl;

    private float minLangScore = 0;

    private int numProcesses = 2;

    private String pythonPath;

    private Map<String, String> langToModelMap = new HashMap<>();

    private Set<String> mimeTypesToIgnore = new HashSet<String>();

    private Set<String> categoriesToIgnore = new HashSet<String>();

    public NamedEntityTaskConfig() {
        this.enabledProp = new EnableTaskProperty(ENABLE_PARAM);
    }

    public String getNerImpl() {
        return this.nerImpl;
    }

    public float getMinLangScore() {
        return minLangScore;
    }

    public int getNumProcesses() {
        return numProcesses;
    }

    public void setNumProcesses(int numProcesses) {
        this.numProcesses = numProcesses;
    }

    public String getPythonPath() {
        return pythonPath;
    }

    public void setPythonPath(String pythonPath) {
        this.pythonPath = pythonPath;
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
    public void processConfig(Path resource) throws IOException {
        String fileName = resource.getFileName().toString();
        if (fileName.equals(Configuration.CONFIG_FILE) || fileName.startsWith("IPEDConfig")) {
            UTF8Properties props = new UTF8Properties();
            props.load(resource.toFile());
            String val = props.getProperty(ENABLE_PARAM_CORRECT);
            if (val == null) {
                val = props.getProperty(ENABLE_PARAM);
            }
            if (val != null) {
                setEnabled(Boolean.valueOf(val.trim()));
            }
        } else {
            processTaskConfig(resource);
        }
    }

    @Override
    public void processProperties(UTF8Properties properties) {

        nerImpl = properties.getProperty("NERImpl"); //$NON-NLS-1$

        String langAndModel;
        int i = 0;
        while ((langAndModel = properties.getProperty("langModel_" + i++)) != null) { //$NON-NLS-1$
            String[] strs = langAndModel.split(":", 2); //$NON-NLS-1$
            if (strs.length == 2) {
                String lang = strs[0].trim();
                String modelPath = strs[1].trim();
                langToModelMap.put(lang, modelPath);
            }
        }

        String mimes = properties.getProperty("mimeTypesToIgnore"); //$NON-NLS-1$
        if (mimes != null) {
            for (String mime : mimes.split(";")) { //$NON-NLS-1$
                mimeTypesToIgnore.add(mime.trim());
            }
        }

        String categories = properties.getProperty("categoriesToIgnore"); //$NON-NLS-1$
        if (categories != null) {
            for (String cat : categories.split(";")) { //$NON-NLS-1$
                categoriesToIgnore.add(cat.trim());
            }
        }

        String minLang = properties.getProperty("minLangScore"); //$NON-NLS-1$
        if (minLang != null) {
            minLangScore = Float.valueOf(minLang.trim());
        }

        String numProc = properties.getProperty("numProcesses"); //$NON-NLS-1$
        if (numProc != null && !numProc.trim().isEmpty()) {
            try {
                numProcesses = Integer.parseInt(numProc.trim());
            } catch (NumberFormatException e) {
                // keep default
            }
        }

        String pyPath = properties.getProperty("pythonPath"); //$NON-NLS-1$
        if (pyPath != null && !pyPath.trim().isEmpty()) {
            pythonPath = pyPath.trim();
        }

    }

}
