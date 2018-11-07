package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
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

import iped3.configuration.ConfigurationDirectory;
import macee.core.Configurable;

public class ConfigurationDirectoryImpl implements ConfigurationDirectory{
	
	List<Path> configDirs = new ArrayList<Path>();
	
	public ConfigurationDirectoryImpl(Path configDir) {
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
		
		FileSystem fs;
		try {
		    fs = FileSystems.getFileSystem(uri);
		    
		}catch(FileSystemNotFoundException e) {
		    Map<String, String> env = new HashMap<String,String>();
            env.put("create", "false");
            if(zip.endsWith(".jar")) {
                env.put("multi-release", "true");
            }            
            fs = FileSystems.newFileSystem(uri, env);
		}
		configDirs.add(fs.getRootDirectories().iterator().next());
	}

	@Override
	public List<Path> lookUpResource(Configurable configurable) throws IOException {
		final DirectoryStream.Filter<Path> filter = configurable.getResourceLookupFilter();

		return lookUpResource(new Predicate<Path>() {
			@Override
			public boolean test(Path path) {
				try {
					return filter.accept(path);
				} catch (IOException e) {
					return false;
				}
			}
		});
		
	}

}
