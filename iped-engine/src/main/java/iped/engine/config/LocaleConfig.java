package iped.engine.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Path;
import java.util.Locale;

import iped.utils.UTF8Properties;

public class LocaleConfig extends AbstractPropertiesConfigurable {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    public static final String CONFIG_FILE = "LocalConfig.txt"; //$NON-NLS-1$
    private static final String HOST_COUNTRY = "hostCountryCode";

    public static final DirectoryStream.Filter<Path> filter = new Filter<Path>() {
        @Override
        public boolean accept(Path entry) throws IOException {
            return entry.endsWith(CONFIG_FILE);
        }
    };

    static {
        String country = System.getProperty(HOST_COUNTRY);
        if (country == null) {
            System.setProperty(HOST_COUNTRY, Locale.getDefault().getCountry());
        }
        Locale.setDefault(Locale.forLanguageTag("en")); //$NON-NLS-1$
    }

    public static String getHostCountry() {
        return System.getProperty(HOST_COUNTRY);
    }

    Locale locale = Locale.getDefault();

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return filter;
    }

    @Override
    public void processProperties(UTF8Properties properties) {

        String value;

        value = properties.getProperty("locale"); //$NON-NLS-1$
        if (value != null && !value.trim().isEmpty())
            locale = Locale.forLanguageTag(value.trim());

        System.setProperty(iped.localization.Messages.LOCALE_SYS_PROP, locale.toLanguageTag()); // $NON-NLS-1$
    }

    public Locale getLocale() {
        return locale;
    }

    @Override
    public void save(Path resource) {
        try {
            File confDir = new File(resource.toFile(), Configuration.CONF_DIR);
            confDir.mkdirs();
            File confFile = new File(confDir, CONFIG_FILE);            
            properties.store(confFile);
        }catch (Exception e) {
            e.printStackTrace();
        }
    }
}
