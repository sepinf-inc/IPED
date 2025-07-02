package iped.localization;

import java.util.Locale;

public class LocaleResolver {

    public static final String LOCALE_SYS_PROP = "iped-locale";

    public static Locale getLocale() {
        String localeStr = getLocaleString();
        return localeStr != null ? Locale.forLanguageTag(localeStr) : Locale.getDefault();
    }

    public static String getLocaleString() {
        return System.getProperty(LOCALE_SYS_PROP);
    }

    public static void setLocale(Locale locale) {
        System.setProperty(LOCALE_SYS_PROP, locale.toLanguageTag());
    }
}
