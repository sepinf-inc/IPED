package iped.engine.config;

import iped.utils.UTF8Properties;

public class RemoteImageClassifierConfig extends AbstractTaskPropertiesConfig {

    private static final String CONFIG_FILE = "RemoteImageClassifier.txt";
    private static final String ENABLE_KEY = "enableRemoteImageClassifier";

    private static final String URL_KEY = "url";
    private static final String BATCH_SIZE_KEY = "batch_size";
    private static final String CATEGORIZATION_THRESHOLD = "categorization_threshold";

    private String url;
    private int batchSize;
    private double categorizationThreshold = 0.5;
    @Override
    void processProperties(UTF8Properties properties) {
        // TODO Auto-generated method stub
        setUrl(properties.getProperty(URL_KEY).trim());
        setBatchSize(Integer.parseInt(properties.getProperty(BATCH_SIZE_KEY).trim()));
        setCategorizationThreshold(Double.parseDouble(properties.getProperty(CATEGORIZATION_THRESHOLD).trim()));

    }

    @Override
    public String getTaskEnableProperty() {
        // TODO Auto-generated method stub
        return ENABLE_KEY;
    }

    @Override
    public String getTaskConfigFileName() {
        // TODO Auto-generated method stub
        return CONFIG_FILE;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public double getCategorizationThreshold() {
        return categorizationThreshold;
    }

    public void setCategorizationThreshold(double categorizationThreshold) {
        this.categorizationThreshold = categorizationThreshold;
    }

}
