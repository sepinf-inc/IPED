package dpf.sp.gpinf.indexer;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Messages {

    private static final String BUNDLE_NAME = "iped-messages"; //$NON-NLS-1$

    private static Logger LOGGER = LoggerFactory.getLogger(Messages.class);

    private static ResourceBundle RESOURCE_BUNDLE;

    private Messages() {
    }

    public static String getString(String key) {
        if (RESOURCE_BUNDLE == null) {
            String localeProp = System.getProperty("iped-locale"); //$NON-NLS-1$
            Locale locale = localeProp != null ? Locale.forLanguageTag(localeProp) : Locale.getDefault();
            RESOURCE_BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME, locale);
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
}
