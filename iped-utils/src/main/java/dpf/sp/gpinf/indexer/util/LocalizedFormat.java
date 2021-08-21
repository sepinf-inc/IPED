package dpf.sp.gpinf.indexer.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.Locale;

public class LocalizedFormat {
    private static final ThreadLocal<NumberFormat> threadLocalNF = new ThreadLocal<NumberFormat>() {
        @Override
        protected NumberFormat initialValue() {
            return getNumberInstance();
        }
    };

    public static String format(Long value) {
        return threadLocalNF.get().format(value);
    }

    public static final DecimalFormat getDecimalInstance(String format) {
        return new DecimalFormat(format, DecimalFormatSymbols.getInstance(getLocale()));
    }

    public static final NumberFormat getNumberInstance() {
        return NumberFormat.getNumberInstance(getLocale());
    }

    private static final Locale getLocale() {
        String localeStr = System.getProperty(iped3.util.Messages.LOCALE_SYS_PROP);
        Locale locale = localeStr != null ? Locale.forLanguageTag(localeStr) : Locale.getDefault();
        return locale;
    }
}
