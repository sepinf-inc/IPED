package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import dpf.sp.gpinf.indexer.util.Util;

import java.nio.file.Path;

public class MakePreviewConfig extends AbstractTaskConfig<List<Set<String>>> {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    private static final String ENABLE_PROP = IPEDConfig.ENABLE_PARSING;

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
    public List<Set<String>> getConfiguration() {
        return Arrays.asList(supportedMimes, supportedMimesWithLinks);
    }

    @Override
    public void setConfiguration(List<Set<String>> config) {
        supportedMimes = config.get(0);
        supportedMimesWithLinks = config.get(1);
    }

    @Override
    public String getTaskEnableProperty() {
        return ENABLE_PROP;
    }

    @Override
    public String getTaskConfigFileName() {
        return CONFIG_FILE;
    }

    @Override
    public void processTaskConfig(Path resource) throws IOException {

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
