package dpf.sp.gpinf.indexer.localization;

import java.text.Collator;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.TreeMap;

public class LocalizedProperties {

    private static final String BUNDLE_NAME = "iped-properties";

    private static ResourceBundle RESOURCE_BUNDLE;

    private static Map<String, String> map = new TreeMap<>(getCollator());
    private static Map<String, String> invertedMap = new TreeMap<>(getCollator());

    private static Collator getCollator() {
        Collator collator = Collator.getInstance();
        collator.setStrength(Collator.PRIMARY);
        return collator;
    }

    private static synchronized void loadLocalizedProps() {
        if (!map.isEmpty()) {
            return;
        }
        String localeStr = System.getProperty(iped3.util.Messages.LOCALE_SYS_PROP); // $NON-NLS-1$
        Locale locale = localeStr != null ? Locale.forLanguageTag(localeStr) : Locale.getDefault();
        RESOURCE_BUNDLE = iped3.util.Messages.getExternalBundle(BUNDLE_NAME, locale);

        Enumeration<String> keys = (Enumeration<String>) RESOURCE_BUNDLE.getKeys();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            String value = RESOURCE_BUNDLE.getString(key).trim();
            map.put(key.toLowerCase().trim(), value);
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
