package dpf.sp.gpinf.indexer.desktop;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class Messages {
	
	private static final String BUNDLE_NAME = "iped-desktop-messages"; //$NON-NLS-1$

	private static ResourceBundle RESOURCE_BUNDLE;

	private Messages() {
	}

	public static String getString(String key) {
		if(RESOURCE_BUNDLE == null) {
		    String localeStr = System.getProperty("iped-locale"); //$NON-NLS-1$
			Locale locale = localeStr != null ? Locale.forLanguageTag(localeStr) : Locale.getDefault();
			RESOURCE_BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME, locale);
		}
		try {
			return RESOURCE_BUNDLE.getString(key);
			
		} catch (MissingResourceException e) {
			e.printStackTrace();
			throw e;
		}
	}
	
	public static void resetLocale() {
	    RESOURCE_BUNDLE = null;
	}
}
