package iped.engine.localization;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Messages {

    private static final String BUNDLE_NAME = "iped-engine-messages"; //$NON-NLS-1$

    private static Logger LOGGER = LoggerFactory.getLogger(Messages.class);

    private static ResourceBundle RESOURCE_BUNDLE;
    public static String TOOLTIP_SUFFIX=".tooltip";
    

    private Messages() {
    }

    public static String getString(String key) {
        if (RESOURCE_BUNDLE == null) {
            String localeProp = System.getProperty(iped.localization.Messages.LOCALE_SYS_PROP); // $NON-NLS-1$
            Locale locale = localeProp != null ? Locale.forLanguageTag(localeProp) : Locale.getDefault();
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

    public static String getString(String key, String defaultValue) {
        if (RESOURCE_BUNDLE == null) {
            String localeProp = System.getProperty(iped.localization.Messages.LOCALE_SYS_PROP); // $NON-NLS-1$
            Locale locale = localeProp != null ? Locale.forLanguageTag(localeProp) : Locale.getDefault();
            RESOURCE_BUNDLE = iped.localization.Messages.getExternalBundle(BUNDLE_NAME, locale);
            String finalLocale = RESOURCE_BUNDLE.getLocale().toLanguageTag();
            if (finalLocale.equals("und")) //$NON-NLS-1$
                finalLocale = "en"; //$NON-NLS-1$
            if (!locale.toLanguageTag().equals(finalLocale))
                LOGGER.error("Bundle for Locale '" + locale.toLanguageTag() //$NON-NLS-1$
                        + "' not found. Using bundle for " + finalLocale); //$NON-NLS-1$
        }
        String result;
        try {
            result = RESOURCE_BUNDLE.getString(key);
            
        } catch (MissingResourceException e) {
            result = defaultValue;
        }
        
        //prepares html formating for tooltips if not already formated as such 
        if(key.endsWith(TOOLTIP_SUFFIX) && !result.startsWith("<html>")) {
            result = "<html>"+result.replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>")+"</html>";
        }
        return result;
    }
}
