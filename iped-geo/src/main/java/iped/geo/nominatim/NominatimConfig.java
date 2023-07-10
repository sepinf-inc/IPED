package iped.geo.nominatim;

import iped.engine.config.AbstractTaskPropertiesConfig;
import iped.utils.UTF8Properties;

public class NominatimConfig extends AbstractTaskPropertiesConfig {
    private static final long serialVersionUID = 1L;

    public static final String ENABLE_PARAM = "enableNominatimLocationResolving";
    private static final String CONF_FILE = "NominatimConfig.txt";

    String protocol = "http";
    String hostName = "10.65.6.2";
    String hostPort = "8080";
    int connectionPoolSize;
    String serviceTestUrlQuery = "/";

    private boolean enableNominatim = false;

    @Override
    public void processProperties(UTF8Properties properties) {
        String value = properties.getProperty("protocol"); //$NON-NLS-1$
        if (value != null) {
            protocol = value.trim();
        }

        value = properties.getProperty("hostName"); //$NON-NLS-1$
        if (value != null) {
            hostName = value.trim();
        }

        value = properties.getProperty("serviceTestUrlQuery"); //$NON-NLS-1$
        if (value != null) {
            serviceTestUrlQuery = value.trim();
        }

        value = properties.getProperty("hostPort"); //$NON-NLS-1$
        if (value != null) {
            hostPort = Integer.toString(Integer.valueOf(value.trim()));
        }

        value = properties.getProperty("connectionPoolSize"); // $NON-NLS-1$
        if (value != null && !value.trim().equalsIgnoreCase("auto")) { //$NON-NLS-1$
            connectionPoolSize = Integer.valueOf(value.trim());
        } else {
            connectionPoolSize = Math.max((int) Math.ceil((float) Runtime.getRuntime().availableProcessors()), 2);
        }
    }

    @Override
    public String getTaskEnableProperty() {
        // TODO Auto-generated method stub
        return ENABLE_PARAM;
    }

    @Override
    public String getTaskConfigFileName() {
        // TODO Auto-generated method stub
        return CONF_FILE;
    }

    public String getProtocol() {
        return protocol;
    }

    public String getHostName() {
        return hostName;
    }

    public String getHostPort() {
        return hostPort;
    }

    public int getConnectionPoolSize() {
        return connectionPoolSize;
    }

    public boolean isEnableNominatim() {
        return enableNominatim;
    }

    public String getServiceTestUrlQuery() {
        return serviceTestUrlQuery;
    }

}
