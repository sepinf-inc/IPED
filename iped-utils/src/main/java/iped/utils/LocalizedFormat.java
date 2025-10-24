package iped.utils;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;

import iped.localization.LocaleResolver;

public class LocalizedFormat {
    //A better description (e.g. Undefined / Indefinido) could be used, but localized
    //strings are not accessible here, so this should be moved or use another solution. 
    private static final String NaN = "NaN";
    
    private static final ThreadLocal<NumberFormat> threadLocalNF = new ThreadLocal<NumberFormat>() {
        @Override
        protected NumberFormat initialValue() {
            return getNumberInstance();
        }
    };

    public static String format(int value) {
        return threadLocalNF.get().format(value);
    }
    
    public static String format(Long value) {
        return threadLocalNF.get().format(value);
    }

    public static final DecimalFormat getDecimalInstance(String format) {
        DecimalFormatSymbols dfs = DecimalFormatSymbols.getInstance(LocaleResolver.getLocale());
        dfs.setNaN(NaN);
        return new DecimalFormat(format, dfs);
    }

    public static final NumberFormat getNumberInstance() {
        NumberFormat nf = NumberFormat.getNumberInstance(LocaleResolver.getLocale());
        if (nf instanceof DecimalFormat) {
            DecimalFormatSymbols dfs = DecimalFormatSymbols.getInstance(LocaleResolver.getLocale());
            dfs.setNaN(NaN);
            ((DecimalFormat) nf).setDecimalFormatSymbols(dfs);
        }
        return nf;
    }
}
