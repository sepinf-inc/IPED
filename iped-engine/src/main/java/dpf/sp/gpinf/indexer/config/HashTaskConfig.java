package dpf.sp.gpinf.indexer.config;

import java.util.ArrayList;

import dpf.sp.gpinf.indexer.util.UTF8Properties;

public class HashTaskConfig extends AbstractTaskPropertiesConfig {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    public static final String ENABLE_PARAM = "enableHash";
    public static final String CONFIG_FILE = "HashTaskConfig.txt";

    private ArrayList<String> algorithms = new ArrayList<>();

    public ArrayList<String> getAlgorithms() {
        return algorithms;
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
    public void processProperties(UTF8Properties properties) {

        String hashes = properties.getProperty("hashes");
        if (hashes != null) {
            hashes = hashes.trim();
            for (String algorithm : hashes.split(";")) {
                algorithms.add(algorithm.trim());
            }
        }

    }

}
