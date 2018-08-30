package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;

import dpf.sp.gpinf.indexer.parsers.OCRParser;

public class OCRConfig extends AbstractPropertiesConfigurable {
	public static final String OCRLanguage = "OCRLanguage";
	public static final String minFileSize2OCR = "minFileSize2OCR";
	public static final String maxFileSize2OCR = "maxFileSize2OCR";
	public static final String pageSegMode = "pageSegMode";

	public static final String CONFIG_FILE = "conf/AdvancedConfig.txt"; //$NON-NLS-1$

	public static final DirectoryStream.Filter<Path> filter = new Filter<Path>() {
		@Override
		public boolean accept(Path entry) throws IOException {
			return entry.endsWith(CONFIG_FILE);
		}
	};

	@Override
	public Filter<Path> getResourceLookupFilter() {
		return filter;
	}

	@Override
	public void processConfig(Path resource) throws IOException {
		super.processConfig(resource);

		String value = null;

	    value = properties.getProperty("enableOCR"); //$NON-NLS-1$
	    if (value != null) {
	      value = value.trim();
	    }

	    if (value != null && !value.isEmpty()) {
	      OCRParser.ENABLED = Boolean.valueOf(value);
	    }
		
		value = properties.getProperty("OCRLanguage"); //$NON-NLS-1$
	    if (value != null) {
	      value = value.trim();
	    }
	    if (value != null && !value.isEmpty()) {
	      OCRParser.LANGUAGE = value;
	    }

	    value = properties.getProperty("minFileSize2OCR"); //$NON-NLS-1$
	    if (value != null) {
	      value = value.trim();
	    }
	    if (value != null && !value.isEmpty()) {
	      OCRParser.MIN_SIZE = Long.valueOf(value);
	    }

	    value = properties.getProperty("maxFileSize2OCR"); //$NON-NLS-1$
	    if (value != null) {
	      value = value.trim();
	    }
	    if (value != null && !value.isEmpty()) {
	      OCRParser.MAX_SIZE = Long.valueOf(value);
	    }

	    value = properties.getProperty("pageSegMode"); //$NON-NLS-1$
	    if (value != null) {
	      value = value.trim();
	    }
	    if (value != null && !value.isEmpty()) {
	      OCRParser.PAGESEGMODE = value;
	    }

	}

}
