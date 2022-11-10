package iped.geo.impl;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import iped.configuration.Configurable;
import iped.engine.config.Configuration;
import iped.utils.UTF8Properties;

public class MapPanelConfig implements Configurable<UTF8Properties> {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public static final String GEO_CONFIG_FILE = "GEOConfig.txt"; //$NON-NLS-1$

    UTF8Properties properties = new UTF8Properties();
    String tileServerUrlPattern;

    HashMap<String, String> defaultTilesSources = new HashMap<String, String>();

    public String getTileServerUrlPattern() {
        return tileServerUrlPattern;
    }

    public static final DirectoryStream.Filter<Path> filter = new Filter<Path>() {
        @Override
        public boolean accept(Path entry) throws IOException {
            return entry.endsWith(GEO_CONFIG_FILE);
        }
    };

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return filter;
    }

    @Override
    public void processConfigs(List<Path> resources) throws IOException {
        for (Iterator<Path> iterator = resources.iterator(); iterator.hasNext();) {
            Path path = iterator.next();
            processConfig(path);
        }
    }

    public void processConfig(Path resource) throws IOException {
        if (resource.endsWith(GEO_CONFIG_FILE)) {
            properties.load(resource.toFile());
            String value;

            value = properties.getProperty("tileServerUrlPattern");
            if (value != null) {
                value = value.trim();
            }
            if (value != null && !value.isEmpty()) {
                tileServerUrlPattern = value;
            }

            Set<Object> keys = properties.keySet();
            for (Iterator<Object> iterator = keys.iterator(); iterator.hasNext();) {
                Object object = iterator.next();
                if (!"tileServerUrlPattern".equals(object.toString())) {
                    defaultTilesSources.put(object.toString(), properties.getProperty(object.toString()));
                }
            }

        }
    }

    public HashMap<String, String> getDefaultTilesSources() {
        return defaultTilesSources;
    }

    @Override
    public UTF8Properties getConfiguration() {
        return properties;
    }

    @Override
    public void setConfiguration(UTF8Properties config) {
        this.properties = config;
    }

    @Override
    public void save(Path resource) {
        try {
            File confDir = new File(resource.toFile(), Configuration.CONF_DIR);
            confDir.mkdirs();
            File confFile = new File(confDir, GEO_CONFIG_FILE);            
            properties.store(confFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
