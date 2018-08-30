package dpf.sp.gpinf.indexer.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.nio.file.DirectoryStream.Filter;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import dpf.sp.gpinf.indexer.parsers.util.PDFToImage;
import dpf.sp.gpinf.indexer.util.UTF8Properties;
import macee.core.Configurable;

public class PluginConfig implements Configurable<UTF8Properties,UTF8Properties>  {

	UTF8Properties properties = new UTF8Properties();
	String optional_jars;
	File optionalJarDir;

	public static final String OPTIONAL_JARS = "optional_jars";
	public static final String LOCAL_CONFIG = "LocalConfig.txt"; //$NON-NLS-1$

	static Set<String> propNames = new HashSet<String>();
	static {
		propNames.add(IPEDConfig.CONFDIR);
		propNames.add(IPEDConfig.TOADDUNALLOCATED);
		propNames.add(IPEDConfig.TOADDFILESLACKS);
		propNames.add(IPEDConfig.CONFIG_FILE);
	}

	public static final DirectoryStream.Filter<Path> filter = new Filter<Path>() {
		@Override
		public boolean accept(Path entry) throws IOException {
			return entry.endsWith(LOCAL_CONFIG);
		}
	};


	public PluginConfig() {
	}	

	@Override
	public UTF8Properties getApplicationConfiguration() {
		return properties;
	}

	@Override
	public void setApplicationConfiguration(UTF8Properties config) {
		properties = config;
	}

	@Override
	public UTF8Properties getUserConfiguration() {
		return null;
	}

	@Override
	public void setUserConfiguration(UTF8Properties config) {
	}

	@Override
	public Set<String> getApplicationPropertyNames() {
		return propNames;
	}

	@Override
	public Filter<Path> getResourceLookupFilter() {
		return filter;
	}

	public File[] getOptionalJars(String appRoot) {
		if(optionalJarDir==null) {
			if(optional_jars!=null) {
		        optionalJarDir = new File(appRoot + "/" + optional_jars.trim()); //$NON-NLS-1$
			}else {
				return null;
			}
		}

	    File[] jars = optionalJarDir.listFiles();
	    if(jars != null) {
	    	for(File jar : jars) {
	    		if(jar.getName().contains("jbig2")) //$NON-NLS-1$
	    			PDFToImage.jbig2LibPath = jar.getAbsolutePath();
	    	}
	    }
	    return jars;
	}

	public String getOptionalJarsPath() {
		return optional_jars;
	}

	@Override
	public void processConfigs(List<Path> resources) throws IOException {
		for (Iterator iterator = resources.iterator(); iterator.hasNext();) {
			Path path = (Path) iterator.next();
			processConfig(path);			
		}
	}

	public void processConfig(Path resource) throws IOException {
		properties.load(resource.toFile());
		optional_jars = properties.getProperty(OPTIONAL_JARS);
	}

}
