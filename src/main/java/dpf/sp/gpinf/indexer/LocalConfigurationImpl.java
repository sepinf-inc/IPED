package dpf.sp.gpinf.indexer;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import iped3.configuration.LocalConfiguration;

public class LocalConfigurationImpl implements LocalConfiguration {
	
	List<Path> configDirs = new ArrayList<Path>();
	Map<URI, FileSystem> zipFileSystems = new HashMap<URI, FileSystem>();
	
	public LocalConfigurationImpl(Path configDir) {
		configDirs.add(configDir);
	}

	@Override
	public List<Path> getResourceLookupFolders() {
		return configDirs;
	}

	@Override
	public List<Path> lookUpResource(Predicate<Path> predicate) throws IOException {
		final List<Path> result = new ArrayList<Path>();

		Consumer<Path> consumer = new Consumer<Path>() {
			@Override
			public void accept(Path t) {
				result.add(t);
			}
		};
		for (Iterator iterator = configDirs.iterator(); iterator.hasNext();) {
			Path path = (Path) iterator.next();
			Files.walk(path).filter(predicate).forEach(consumer);
		}

		return result;
	}

	public void addPath(Path path) {
		configDirs.add(path);
	}

	public void addZip(Path zip) throws IOException {
		zip=zip.normalize();

		URI uri;
		try {
			uri = new URI("jar:"+zip.toUri().toString());
		} catch (URISyntaxException e) {
			throw new IOException(e);
		}
		
		FileSystem fs = zipFileSystems.get(uri);
		if(fs==null) {
			Map<String, String> env = new HashMap<String,String>();
	        env.put("create", "false");

	        if(zip.endsWith(".jar")) {
	        	env.put("multi-release", "true");
	        }
			fs = FileSystems.newFileSystem(uri, env);
			configDirs.add(fs.getRootDirectories().iterator().next());
		}
	}

}
