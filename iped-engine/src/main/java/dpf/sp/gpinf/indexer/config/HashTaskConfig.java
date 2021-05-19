package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Path;
import java.util.ArrayList;

public class HashTaskConfig extends AbstractPropertiesConfigurable {

    public static final String HASH_PROP = "hash";

    private ArrayList<String> algorithms = new ArrayList<>();

    public ArrayList<String> getAlgorithms() {
        return algorithms;
    }

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return new Filter<Path>() {
            @Override
            public boolean accept(Path entry) throws IOException {
                return entry.endsWith(IPEDConfig.CONFIG_FILE);
            }
        };
    }

    @Override
    public void processConfig(Path resource) throws IOException {

        properties.load(resource.toFile());

        String value = properties.getProperty(HASH_PROP);
        if (value != null && !(value = value.trim()).isEmpty()) {
            for (String algorithm : value.split(";")) {
                algorithms.add(algorithm.trim());
            }
        }
    }

}
