package iped.engine.config;

import iped.utils.UTF8Properties;

public class ElasticSearchTaskConfig extends AbstractTaskPropertiesConfig {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private static final String CONF_FILE_NAME = "ElasticSearchConfig.txt";

    private static final String ENABLED_KEY = "enableIndexToElasticSearch";
    private static final String HOST_KEY = "host";
    private static final String PORT_KEY = "port";
    private static final String PROTOCOL_KEY = "protocol";
    private static final String MAX_FIELDS_KEY = "index.mapping.total_fields.limit";
    private static final String INDEX_SHARDS_KEY = "index.number_of_shards";
    private static final String INDEX_REPLICAS_KEY = "index.number_of_replicas";
    private static final String INDEX_POLICY_KEY = "index.lifecycle.name";
    private static final String MIN_BULK_SIZE_KEY = "min_bulk_size";
    private static final String MIN_BULK_ITEMS_KEY = "min_bulk_items";
    private static final String MAX_ASYNC_REQUESTS_KEY = "max_async_requests";
    private static final String TIMEOUT_MILLIS_KEY = "timeout_millis";
    private static final String CONNECT_TIMEOUT_KEY = "connect_timeout_millis";
    private static final String CUSTOM_ANALYZER_KEY = "useCustomAnalyzer";
    private static final String VALIDATE_SSL = "validateSSL";
    private static final String TERM_VECTOR = "useTermVector";

    private String host;
    private int port = 9200;
    private String protocol;
    private int max_fields = 10000;
    private int min_bulk_size = 1 << 23;
    private int min_bulk_items = 1000;
    private int connect_timeout = 5000;
    private int timeout_millis = 3600000;
    private int max_async_requests = 5;
    private int index_shards = 1;
    private int index_replicas = 1;
    private String index_policy = "";
    private boolean useCustomAnalyzer;
    private boolean validateSSL = true;
    private boolean termVector = false;

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getProtocol() {
        return protocol;
    }

    public int getMax_fields() {
        return max_fields;
    }

    public int getMin_bulk_size() {
        return min_bulk_size;
    }

    public int getMin_bulk_items() {
        return min_bulk_items;
    }

    public int getConnect_timeout() {
        return connect_timeout;
    }

    public int getTimeout_millis() {
        return timeout_millis;
    }

    public int getMax_async_requests() {
        return max_async_requests;
    }

    public int getIndex_shards() {
        return index_shards;
    }

    public int getIndex_replicas() {
        return index_replicas;
    }

    public String getIndex_policy() {
        return index_policy;
    }

    public boolean isUseCustomAnalyzer() {
        return useCustomAnalyzer;
    }

    @Override
    public String getTaskEnableProperty() {
        return ENABLED_KEY;
    }

    @Override
    public String getTaskConfigFileName() {
        return CONF_FILE_NAME;
    }

    @Override
    public void processProperties(UTF8Properties props) {

        host = props.getProperty(HOST_KEY).trim();
        port = Integer.valueOf(props.getProperty(PORT_KEY).trim());
        protocol = props.getProperty(PROTOCOL_KEY);
        max_fields = Integer.valueOf(props.getProperty(MAX_FIELDS_KEY).trim());
        min_bulk_size = Integer.valueOf(props.getProperty(MIN_BULK_SIZE_KEY).trim());
        min_bulk_items = Integer.valueOf(props.getProperty(MIN_BULK_ITEMS_KEY).trim());
        connect_timeout = Integer.valueOf(props.getProperty(CONNECT_TIMEOUT_KEY).trim());
        timeout_millis = Integer.valueOf(props.getProperty(TIMEOUT_MILLIS_KEY).trim());
        max_async_requests = Integer.valueOf(props.getProperty(MAX_ASYNC_REQUESTS_KEY).trim());
        index_shards = Integer.valueOf(props.getProperty(INDEX_SHARDS_KEY).trim());
        index_replicas = Integer.valueOf(props.getProperty(INDEX_REPLICAS_KEY).trim());
        index_policy = props.getProperty(INDEX_POLICY_KEY);
        index_policy = index_policy == null ? "" : index_policy.trim();
        useCustomAnalyzer = Boolean.valueOf(props.getProperty(CUSTOM_ANALYZER_KEY).trim());
        validateSSL = Boolean.valueOf(props.getProperty(VALIDATE_SSL).trim());
        String value = props.getProperty(TERM_VECTOR);
        if (value != null) {
            termVector = Boolean.valueOf(value.trim());
        }
    }

    public boolean getValidateSSL() {
        return validateSSL;
    }

    public boolean isTermVector() {
        return termVector;
    }

}
