package dpf.sp.gpinf.indexer.config;

import java.io.IOException;
import java.nio.file.DirectoryStream.Filter;
import java.util.ArrayList;
import java.util.List;

import dpf.sp.gpinf.indexer.util.UTF8Properties;

import java.nio.file.Path;

public class AudioTranscriptConfig extends AbstractPropertiesConfigurable {

    private static final String CONF_FILE = "AudioTranscriptConfig.txt";
    private static final String ENABLE_KEY = "enableAudioTranscription";
    private static final String IMPL_CLASS_KEY = "implementationClass";
    private static final String REGION_KEY = "serviceRegion";
    private static final String TIMEOUT_KEY = "timeout";
    private static final String LANG_KEY = "language";
    private static final String MIMES_KEY = "mimesToProcess";
    private static final String CONVERT_CMD_KEY = "convertCommand";
    private static final String REQUEST_INTERVAL_KEY = "requestIntervalMillis";
    private static final String MAX_REQUESTS_KEY = "maxConcurrentRequests";

    private static final String LANG_AUTO_VAL = "auto";

    private boolean isEnabled = false;
    private List<String> languages = new ArrayList<>();
    private List<String> mimesToProcess = new ArrayList<>();
    private String className;
    private String serviceRegion;
    private int timeoutPerSec;
    private String convertCmd;
    private int requestIntervalMillis = 0;
    private int maxConcurrentRequests;

    public String getServiceRegion() {
        return serviceRegion;
    }

    public int getRequestIntervalMillis() {
        return requestIntervalMillis;
    }

    public int getMaxConcurrentRequests() {
        return maxConcurrentRequests;
    }

    public boolean isEnabled() {
        return isEnabled;
    }

    public List<String> getLanguages() {
        return languages;
    }

    public List<String> getMimesToProcess() {
        return mimesToProcess;
    }

    public String getClassName() {
        return className;
    }

    public int getTimeoutPerSec() {
        return timeoutPerSec;
    }

    public String getConvertCmd() {
        return convertCmd;
    }

    @Override
    public Filter<Path> getResourceLookupFilter() {
        return new Filter<Path>() {
            @Override
            public boolean accept(Path entry) throws IOException {
                return entry.endsWith(CONF_FILE) || entry.endsWith(IPEDConfig.CONFIG_FILE);
            }
        };
    }

    @Override
    public void processConfig(Path resource) throws IOException {

        UTF8Properties props = super.properties;
        props.load(resource.toFile());

        if (IPEDConfig.CONFIG_FILE.equals(resource.getFileName().toString())) {
            String enabled = props.getProperty(ENABLE_KEY);
            if (enabled != null) {
                isEnabled = Boolean.valueOf(enabled.trim());
            }
        } else {
            String langs = props.getProperty(LANG_KEY).trim();
            if (LANG_AUTO_VAL.equalsIgnoreCase(langs)) {
                languages.add(System.getProperty(iped3.util.Messages.LOCALE_SYS_PROP));
            } else {
                for (String lang : langs.split(";")) {
                    languages.add(lang.trim());
                }
            }

            String mimes = props.getProperty(MIMES_KEY).trim();
            for (String mime : mimes.split(";")) {
                mimesToProcess.add(mime.trim());
            }

            className = props.getProperty(IMPL_CLASS_KEY).trim();
            serviceRegion = props.getProperty(REGION_KEY).trim();
            convertCmd = props.getProperty(CONVERT_CMD_KEY).trim();
            timeoutPerSec = Integer.valueOf(props.getProperty(TIMEOUT_KEY).trim());
            requestIntervalMillis = Integer.valueOf(props.getProperty(REQUEST_INTERVAL_KEY).trim());
            maxConcurrentRequests = Integer.valueOf(props.getProperty(MAX_REQUESTS_KEY).trim());
        }

    }

}
