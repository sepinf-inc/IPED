package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import dpf.sp.gpinf.indexer.util.UTF8Properties;
import macee.core.Configurable;

public abstract class AbstractPropertiesConfigurable implements Configurable<UTF8Properties, UTF8Properties> {
	UTF8Properties properties = new UTF8Properties();
	UTF8Properties userProperties = new UTF8Properties();
	
	Set<String> propNames = new HashSet<String>();

	@Override
	public Set<String> getApplicationPropertyNames() {
		return propNames;
	}
	
	@Override
	public void processConfigs(List<Path> resources) throws IOException {
		for (Iterator<Path> iterator = resources.iterator(); iterator.hasNext();) {
			Path path = iterator.next();
			processConfig(path);
		}
	}

	public void processConfig(Path resource) throws IOException {
		properties.load(resource.toFile());
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
	public UTF8Properties getUserConfiguration() {
		return userProperties;
	}

	@Override
	public void setUserConfiguration(UTF8Properties config) {
		// TODO Auto-generated method stub
		
	}
}
