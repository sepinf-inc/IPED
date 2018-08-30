package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.nio.file.DirectoryStream.Filter;

import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.impl.NoOpLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dpf.sp.gpinf.indexer.Configuration;
import dpf.sp.gpinf.indexer.util.CustomLoader.CustomURLClassLoader;

public class KFFConfig extends AbstractPropertiesConfigurable{
	public static final String CONFIG_FILE = "LocalConfig.txt"; //$NON-NLS-1$
	public static final String IPEDCONFIGFILE = "IPEDConfig.txt"; //$NON-NLS-1$

	public static final DirectoryStream.Filter<Path> filter = new Filter<Path>() {
		@Override
		public boolean accept(Path entry) throws IOException {
			return entry.endsWith(CONFIG_FILE) || entry.endsWith(IPEDCONFIGFILE);
		}
	};

	String kffDb;
	boolean enableKff;

	@Override
	public Filter<Path> getResourceLookupFilter() {
		return filter;
	}

	Logger getLogger(Path resource) {
	    // DataSource.testConnection(configPathStr);
	    LogFactory.getFactory().setAttribute("org.apache.commons.logging.Log", NoOpLog.class.getName()); //$NON-NLS-1$

	    Logger LOGGER = null;
	    if(Configuration.class.getClassLoader().getClass().getName()
	            .equals(CustomURLClassLoader.class.getName()))
	        LOGGER = LoggerFactory.getLogger(Configuration.class);

	    if(LOGGER != null) LOGGER.info("Loading configuration from " + resource.toAbsolutePath()); //$NON-NLS-1$

	    return LOGGER;
	}
	
	public void processConfig(Path resource) throws IOException {
		super.processConfig(resource);

		String value;

		if(resource.endsWith(IPEDCONFIGFILE)) {
			value = properties.getProperty("enableKff"); //$NON-NLS-1$
		    if (value != null) {
		      value = value.trim();
		    }
		    if (value != null && !value.isEmpty()) {
		    	enableKff = Boolean.valueOf(value);
		    }
		}

		if(resource.endsWith(CONFIG_FILE)) {
			value = properties.getProperty("kffDb"); //$NON-NLS-1$
		    if (value != null) {
		      value = value.trim();
		    }
		    if (value != null && !value.isEmpty()) {
		    	kffDb = value;
		    }
		}
	}


}
