package iped.localization;

import java.util.Enumeration;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.TreeMap;

public class LocalizedProperties {

    private static final String BUNDLE_NAME = "iped-properties";

    private static ResourceBundle RESOURCE_BUNDLE;

    private static Map<String, String> map = new TreeMap<>();
    private static Map<String, String> invertedMap = new TreeMap<>();

    private static synchronized void loadLocalizedProps() {
        if (!map.isEmpty()) {
            return;
        }
        RESOURCE_BUNDLE = iped.localization.Messages.getExternalBundle(BUNDLE_NAME, LocaleResolver.getLocale());

        Enumeration<String> keys = (Enumeration<String>) RESOURCE_BUNDLE.getKeys();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            String value = RESOURCE_BUNDLE.getString(key).trim();
            map.put(key.trim(), value);
            invertedMap.put(value, key.trim());
        }
    }

    public static String getNonLocalizedField(String localizedField) {
        if (invertedMap.isEmpty()) {
            loadLocalizedProps();
        }
        return invertedMap.getOrDefault(localizedField, localizedField);
    }

    public static String getLocalizedField(String nonLocalizedField) {
        if (map.isEmpty()) {
            loadLocalizedProps();
        }
        return map.getOrDefault(nonLocalizedField, nonLocalizedField);
    }

}
