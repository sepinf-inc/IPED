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
		if(RESOURCE_BUNDLE == null) {
			Locale locale = Locale.forLanguageTag(System.getProperty("iped-locale")); //$NON-NLS-1$
			RESOURCE_BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME, locale);
			if(!locale.equals(RESOURCE_BUNDLE.getLocale()))
				LOGGER.error("Bundle for Locale '" + locale.toLanguageTag() //$NON-NLS-1$
				+ "' not found. Using bundle for " + RESOURCE_BUNDLE.getLocale().toLanguageTag()); //$NON-NLS-1$
		}
		try {
			return RESOURCE_BUNDLE.getString(key);
			
		} catch (MissingResourceException e) {
			e.printStackTrace();
			throw e;
		}
	}
}
