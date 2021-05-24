package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.DirectoryStream.Filter;
import java.util.HashSet;
import java.util.Set;

import dpf.sp.gpinf.indexer.util.Util;

import java.nio.file.Path;

public class MakePreviewConfig extends AbstractPropertiesConfigurable {

    private static final String CONFIG_FILE = "MakePreviewConfig.txt";

    private static final String SUPPORTED_KEY = "supportedMimes";

    private static final String SUPPORTED_LINKS_KEY = "supportedMimesWithLinks";

    private Set<String> supportedMimes = new HashSet<>();

    private Set<String> supportedMimesWithLinks = new HashSet<>();

    public Set<String> getSupportedMimes() {
        return supportedMimes;
    }

    public Set<String> getSupportedMimesWithLinks() {
        return supportedMimesWithLinks;
    }

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return new Filter<Path>() {
            @Override
            public boolean accept(Path entry) throws IOException {
                return entry.endsWith(CONFIG_FILE);
            }
        };
    }

    @Override
    public void processConfig(Path resource) throws IOException {

        String content = Util.readUTF8Content(resource.toFile());
        for (String line : content.split("\n")) { //$NON-NLS-1$
            if (line.trim().startsWith("#") || line.trim().isEmpty()) { //$NON-NLS-1$
                continue;
            }
            if (line.startsWith(SUPPORTED_KEY) || line.startsWith(SUPPORTED_LINKS_KEY))
                for (String mime : line.substring(line.indexOf('=') + 1).split(";")) {
                    if (line.startsWith(SUPPORTED_LINKS_KEY))
                        supportedMimesWithLinks.add(mime.trim());
                    else if (line.startsWith(SUPPORTED_KEY))
                        supportedMimes.add(mime.trim());
                }
        }

    }

}
