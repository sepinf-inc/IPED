package dpf.sp.gpinf.indexer.parsers.util;

import iped3.io.IItemBase;
import iped3.util.ExtraProperties;

public class PhoneParsingConfig {

    public static final String PHONE_PARSERS_KEY = "iped.phoneParsersKey";
    public static final String PHONE_PARSERS_VAL_EXTERNAL = "iped.PhoneParsers.external";

    private static String ufdrSourceReaderName = null;

    public static final void enableExternalPhoneParsersOnly() {
        System.setProperty(PHONE_PARSERS_KEY, PHONE_PARSERS_VAL_EXTERNAL);
    }

    public static final boolean isExternalPhoneParsersOnly() {
        return PHONE_PARSERS_VAL_EXTERNAL.equals(System.getProperty(PHONE_PARSERS_KEY));
    }

    public static final void setUfdrReaderName(String ufdrReaderName) {
        ufdrSourceReaderName = ufdrReaderName;
    }

    public static final boolean isFromUfdrDatasourceReader(IItemBase item) {
        return item != null && ufdrSourceReaderName != null
                && ufdrSourceReaderName.equals(item.getExtraAttribute(ExtraProperties.DATASOURCE_READER));
    }

}
