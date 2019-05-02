package dpf.sp.gpinf.indexer.config;

import java.nio.file.DirectoryStream.Filter;
import java.util.Locale;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;

public class LocaleConfig extends AbstractPropertiesConfigurable {
	public static final String CONFIG_FILE = "LocalConfig.txt"; //$NON-NLS-1$

	public static final DirectoryStream.Filter<Path> filter = new Filter<Path>() {
		@Override
		public boolean accept(Path entry) throws IOException {
			return entry.endsWith(CONFIG_FILE);
		}
	};
	
	static {
	    Locale.setDefault(Locale.forLanguageTag("en")); //$NON-NLS-1$
	}

	Locale locale = Locale.getDefault();

	@Override
	public Filter<Path> getResourceLookupFilter() {
		return filter;
	}

	public void processConfig(Path resource) throws IOException {
		super.processConfig(resource);

		String value;

	    value = properties.getProperty("locale"); //$NON-NLS-1$
	    if (value != null && !value.trim().isEmpty())
	      locale = Locale.forLanguageTag(value.trim());

	    System.setProperty("iped-locale", locale.toLanguageTag()); //$NON-NLS-1$
	}

	public Locale getLocale() {
		return locale;
	}
}
