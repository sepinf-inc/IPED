package iped.app.ui;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import iped.localization.LocaleResolver;

public class Messages {

    private static final String BUNDLE_NAME = "iped-desktop-messages"; //$NON-NLS-1$

    private static ResourceBundle RESOURCE_BUNDLE;
    public static final String TOOLTIP_NAME_SUFFIX = ".tooltip";

    private Messages() {
    }

    public static String get(String key) {
        if (RESOURCE_BUNDLE == null) {
            RESOURCE_BUNDLE = iped.localization.Messages.getExternalBundle(BUNDLE_NAME, LocaleResolver.getLocale());
        }
        try {
            return RESOURCE_BUNDLE.getString(key);

        } catch (MissingResourceException e) {
            e.printStackTrace();
            throw e;
        }
    }

    public static List<String> getKeys(String prefix) {
        if (RESOURCE_BUNDLE == null) {
            RESOURCE_BUNDLE = iped.localization.Messages.getExternalBundle(BUNDLE_NAME, LocaleResolver.getLocale());
        }
        try {
            List<String> ret = new ArrayList<String>();
            Enumeration<String> keys = RESOURCE_BUNDLE.getKeys();
            while (keys.hasMoreElements()) {
                String key = keys.nextElement();
                if (key.startsWith(prefix))
                    ret.add(key);
            }
            return ret;

        } catch (MissingResourceException e) {
            e.printStackTrace();
            throw e;
        }
    }

    public static String getString(String key, Object... args) {
        String value = get(key);
        if (args != null)
            value = MessageFormat.format(value, args);
        return value;
    }

    public static void resetLocale() {
        RESOURCE_BUNDLE = null;
    }
}
