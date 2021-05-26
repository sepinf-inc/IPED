package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;

public class HashTaskConfig extends AbstractTaskPropertiesConfig {

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
    public void processTaskConfig(Path resource) throws IOException {

        properties.load(resource.toFile());
        String hashes = properties.getProperty("hashes");
        if (hashes != null) {
            hashes = hashes.trim();
            for (String algorithm : hashes.split(";")) {
                algorithms.add(algorithm.trim());
            }
        }

    }

}
