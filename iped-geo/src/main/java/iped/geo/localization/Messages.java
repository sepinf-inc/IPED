package iped.geo.localization;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.localization.LocaleResolver;

public class Messages {

    private static final String BUNDLE_NAME = "iped-geo-messages"; //$NON-NLS-1$

    private static Logger LOGGER = LoggerFactory.getLogger(Messages.class);

    private static ResourceBundle RESOURCE_BUNDLE;

    private Messages() {
    }

    public static String getString(String key) {
        if (RESOURCE_BUNDLE == null) {
            Locale locale = LocaleResolver.getLocale();
            RESOURCE_BUNDLE = iped.localization.Messages.getExternalBundle(BUNDLE_NAME, locale);
            String finalLocale = RESOURCE_BUNDLE.getLocale().toLanguageTag();

            if (finalLocale.equals("und")) //$NON-NLS-1$
                finalLocale = "en"; //$NON-NLS-1$
            if (!locale.toLanguageTag().equals(finalLocale))
                LOGGER.error("Bundle for Locale '" + locale.toLanguageTag() //$NON-NLS-1$
                        + "' not found. Using bundle for " + finalLocale); //$NON-NLS-1$
        }
        try {
            return RESOURCE_BUNDLE.getString(key);

        } catch (MissingResourceException e) {
            e.printStackTrace();
            throw e;
        }
    }

    public static Set<String> getKeys() {
        return RESOURCE_BUNDLE.keySet();
    }
}
