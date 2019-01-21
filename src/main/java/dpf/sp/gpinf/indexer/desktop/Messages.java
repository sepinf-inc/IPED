package dpf.sp.gpinf.indexer.desktop;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public class Messages {
	
	private static final String BUNDLE_NAME = "iped-desktop-messages"; //$NON-NLS-1$

	private static ResourceBundle RESOURCE_BUNDLE;

	private Messages() {
	}
	
  public static String getString(String key, Object... args) {
    String value = getString(key);
    value = MessageFormat.format(value, args);
    return value;
  }

	public static String getString(String key) {
		if(RESOURCE_BUNDLE == null) {
			Locale locale = Locale.forLanguageTag(System.getProperty("iped-locale")); //$NON-NLS-1$
			RESOURCE_BUNDLE = ResourceBundle.getBundle(BUNDLE_NAME, locale);
		}
		return RESOURCE_BUNDLE.getString(key);
	}
	
  public static void resetLocale() {
    RESOURCE_BUNDLE = null;
  }

  public static String get(String key) {
    try {
      return getString(key);
    } catch (MissingResourceException e) {
      return key;
    }
  }
}
