package iped.engine.localization;

import java.text.Collator;
import java.util.Enumeration;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.TreeMap;

import iped.localization.LocaleResolver;

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
        RESOURCE_BUNDLE = iped.localization.Messages.getExternalBundle(BUNDLE_NAME, LocaleResolver.getLocale());

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
