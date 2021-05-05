package dpf.sp.gpinf.indexer.config;

import java.nio.file.DirectoryStream.Filter;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.text.Collator;
import java.util.Enumeration;
import java.util.Map;
import java.util.TreeMap;

public class CategoryLocalization extends AbstractPropertiesConfigurable {

    private static final String CONF_PREFIX = "categories";
    private static final String CONF_SUFFIX = ".properties";

    private static Collator collator;

    private Map<String, String> map = new TreeMap<>(getCollator());
    private Map<String, String> invertedMap = new TreeMap<>(getCollator());

    private static Collator getCollator() {
        if (collator == null) {
            synchronized (CategoryLocalization.class) {
                if (collator == null) {
                    collator = Collator.getInstance();
                    collator.setStrength(Collator.PRIMARY);
                }
            }
        }
        return collator;
    }

    private DirectoryStream.Filter<Path> filter = new Filter<Path>() {
        @Override
        public boolean accept(Path entry) throws IOException {
            String confFile = CONF_PREFIX + "_" + System.getProperty(LocaleConfig.LOCALE_SYS_PROP) + CONF_SUFFIX;
            return entry.endsWith(confFile);
        }
    };

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return filter;
    }

    @Override
    public void processConfig(Path resource) throws IOException {
        super.processConfig(resource);
        Enumeration<String> keys = (Enumeration<String>) super.properties.propertyNames();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            map.put(key.toLowerCase(), super.properties.getProperty(key));
            invertedMap.put(super.properties.getProperty(key), key);
        }
    }

    public String getLocalizedCategory(String category) {
        return map.getOrDefault(category, category);
    }

    public String getNonLocalizedCategory(String category) {
        return invertedMap.getOrDefault(category, category);
    }

}
