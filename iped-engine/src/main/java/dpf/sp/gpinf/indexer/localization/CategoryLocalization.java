package dpf.sp.gpinf.indexer.localization;

import java.text.Collator;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.TreeMap;

public class CategoryLocalization {

    private static final String BUNDLE_NAME = "iped-categories";

    private static ResourceBundle RESOURCE_BUNDLE;
    private static Collator collator;
    private static CategoryLocalization instance;

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

    public static CategoryLocalization getInstance() {
        if (instance == null) {
            synchronized (CategoryLocalization.class) {
                if (instance == null) {
                    instance = new CategoryLocalization();
                }
            }
        }
        return instance;
    }

    private CategoryLocalization() {
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

    public String getLocalizedCategory(String category) {
        return map.getOrDefault(category, category);
    }

    public String getNonLocalizedCategory(String category) {
        return invertedMap.getOrDefault(category, category);
    }

}
