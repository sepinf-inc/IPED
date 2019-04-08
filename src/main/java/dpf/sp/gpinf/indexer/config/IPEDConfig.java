package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;

import dpf.sp.gpinf.indexer.parsers.IndexerDefaultParser;

import java.nio.file.Path;

public class IPEDConfig extends AbstractPropertiesConfigurable {
	boolean toAddUnallocated = false;
	boolean toAddFileSlacks = false;

	String confDir;

	public static final String CONFDIR = "confdir";
	public static final String TOADDUNALLOCATED = "addUnallocated";
	public static final String TOADDFILESLACKS = "addFileSlacks";
	public static final String CONFIG_FILE = "IPEDConfig.txt"; //$NON-NLS-1$

	public static final DirectoryStream.Filter<Path> filter = new Filter<Path>() {
		@Override
		public boolean accept(Path entry) throws IOException {
			return entry.endsWith(CONFIG_FILE);
		}
	};

	public IPEDConfig() {
		propNames.add(IPEDConfig.CONFDIR);
		propNames.add(IPEDConfig.TOADDUNALLOCATED);
		propNames.add(IPEDConfig.TOADDFILESLACKS);
	}

	public String getConfDir() {
		return (String) properties.get(IPEDConfig.CONFDIR);
	}

	public void setConfDir(String confDir) {
		properties.put(IPEDConfig.CONFDIR, confDir);
	}

	public boolean isToAddUnallocated() {
		return "true".equals(properties.get(IPEDConfig.TOADDUNALLOCATED));
	}

	public void setToAddUnallocated(boolean toAddUnallocated) {
		properties.put(IPEDConfig.TOADDUNALLOCATED, new Boolean(toAddUnallocated).toString());		
	}

	public boolean isToAddFileSlacks() {
		return "true".equals(properties.get(IPEDConfig.TOADDFILESLACKS));
	}

	@Override
	public Filter<Path> getResourceLookupFilter() {
		return filter;
	}

	public void processConfig(Path resource) throws IOException {
		super.processConfig(resource);

		String value = null;

		value = properties.getProperty(TOADDUNALLOCATED); //$NON-NLS-1$
	    if (value != null) {
	      value = value.trim();
	    }
	    if (value != null && !value.isEmpty()) {
	      toAddUnallocated = Boolean.valueOf(value);
	    }

		value = properties.getProperty(TOADDFILESLACKS); //$NON-NLS-1$
	    if (value != null) {
	      value = value.trim();
	    }
	    if (value != null && !value.isEmpty()) {
	    	toAddFileSlacks = Boolean.valueOf(value);
	    }
	    
	    value = properties.getProperty("indexUnknownFiles"); //$NON-NLS-1$
	    if (value != null && !value.trim().isEmpty()) {
	        System.setProperty(IndexerDefaultParser.FALLBACK_PARSER_PROP, value.trim());
	    }
	    
	    value = properties.getProperty("indexCorruptedFiles"); //$NON-NLS-1$
	    if (value != null && !value.trim().isEmpty()) {
	        System.setProperty(IndexerDefaultParser.ERROR_PARSER_PROP, value.trim());
	    }
	    
	
	}	

}
