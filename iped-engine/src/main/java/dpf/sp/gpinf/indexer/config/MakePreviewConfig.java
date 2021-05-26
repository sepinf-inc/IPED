package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import dpf.sp.gpinf.indexer.util.Util;

import java.nio.file.Path;

public class MakePreviewConfig extends AbstractTaskConfig<Object> {

    private static final String ENABLE_PROP = IPEDConfig.ENABLE_PARSING;

    private static final String CONFIG_FILE = "MakePreviewConfig.txt";

    private static final String SUPPORTED_KEY = "supportedMimes";

    private static final String SUPPORTED_LINKS_KEY = "supportedMimesWithLinks";

    public static class MakePreviewMimes {

        private Set<String> supportedMimes = new HashSet<>();

        private Set<String> supportedMimesWithLinks = new HashSet<>();
    }

    private MakePreviewMimes mimes = new MakePreviewMimes();

    public Set<String> getSupportedMimes() {
        return mimes.supportedMimes;
    }

    public Set<String> getSupportedMimesWithLinks() {
        return mimes.supportedMimesWithLinks;
    }

    @Override
    public Object getConfiguration() {
        return mimes;
    }

    @Override
    public void setConfiguration(Object config) {
        mimes = (MakePreviewMimes) config;
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
                        mimes.supportedMimesWithLinks.add(mime.trim());
                    else if (line.startsWith(SUPPORTED_KEY))
                        mimes.supportedMimes.add(mime.trim());
                }
        }

    }

}
