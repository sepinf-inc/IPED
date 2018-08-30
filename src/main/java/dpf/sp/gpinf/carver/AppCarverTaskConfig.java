package dpf.sp.gpinf.carver;

import java.io.IOException;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import dpf.sp.gpinf.carver.api.CarverConfiguration;
import dpf.sp.gpinf.indexer.util.UTF8Properties;
import macee.core.Configurable;

public class AppCarverTaskConfig implements Configurable<UTF8Properties, UTF8Properties>{
	public static final String IPEDCONFIG = "IPEDConfig.txt"; //$NON-NLS-1$
	public static final String CARVER_CONFIG_PREFIX = "carver-"; //$NON-NLS-1$
	public static final String CARVER_CONFIG_SUFFIX = ".xml"; //$NON-NLS-1$
	public static final String GLOBAL_CARVER_CONFIG = "conf\\CarverConfig.xml"; //$NON-NLS-1$
	
	UTF8Properties properties = new UTF8Properties();

	public static final DirectoryStream.Filter<Path> filter = new Filter<Path>() {
		@Override
		public boolean accept(Path entry) throws IOException {
			return entry.endsWith(IPEDCONFIG) ||entry.endsWith(GLOBAL_CARVER_CONFIG) || (entry.getFileName().startsWith(CARVER_CONFIG_PREFIX)&&entry.getFileName().startsWith(CARVER_CONFIG_SUFFIX));
		}
	};
	
	boolean carvingEnabled = true;
	XMLCarverConfiguration carverConfiguration = new XMLCarverConfiguration();

	public CarverConfiguration getCarverConfiguration() {
		return carverConfiguration;
	}

	public boolean getCarvingEnabled() {
		return carvingEnabled;
	}

	@Override
	public Filter<Path> getResourceLookupFilter() {
		return filter;
	}

	@Override
	public UTF8Properties getApplicationConfiguration() {
		return properties;
	}

	@Override
	public void setApplicationConfiguration(UTF8Properties config) {
		// TODO Auto-generated method stub
	}

	@Override
	public Set<String> getApplicationPropertyNames() {
		return properties.stringPropertyNames();
	}

	@Override
	public UTF8Properties getUserConfiguration() {
		return properties;
	}

	@Override
	public void setUserConfiguration(UTF8Properties config) {
		// TODO Auto-generated method stub		
	}
	
	@Override
	public void processConfigs(List<Path> resources) throws IOException {
		for (Iterator<Path> iterator = resources.iterator(); iterator.hasNext();) {
			Path path = iterator.next();
			processConfig(path);
		}
	}

	public void processConfig(Path resource) throws IOException {
		if(resource.endsWith(IPEDCONFIG)) {
			properties.load(resource.toFile());
			String value;
			
			value = properties.getProperty("enableCarving");
		    if (value != null) {
			      value = value.trim();
		    }
		    if (value != null && !value.isEmpty()) {
		      carvingEnabled = Boolean.valueOf(value);
		    }
			
		}
		if(resource.endsWith(GLOBAL_CARVER_CONFIG)) {
			carverConfiguration.loadXMLConfigFile(resource.toFile());
		}
		if(resource.getFileName().startsWith(CARVER_CONFIG_PREFIX)&&resource.getFileName().startsWith(CARVER_CONFIG_SUFFIX)) {
			carverConfiguration.loadXMLConfigFile(resource.toFile());
		}
	}

}
