package iped.engine.config;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import iped.utils.UTF8Properties;

public class AudioTranscriptConfig extends AbstractTaskPropertiesConfig {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    public static final String CONF_FILE = "AudioTranscriptConfig.txt";
    private static final String ENABLE_KEY = "enableAudioTranscription";
    private static final String IMPL_CLASS_KEY = "implementationClass";
    private static final String REGION_KEY = "serviceRegion";
    private static final String MIN_TIMEOUT_KEY = "minTimeout";
    private static final String TIMEOUT_PER_SEC_KEY = "timeoutPerSec";
    private static final String LANG_KEY = "language";
    private static final String MIMES_KEY = "mimesToProcess";
    private static final String CONVERT_CMD_KEY = "convertCommand";
    private static final String REQUEST_INTERVAL_KEY = "requestIntervalMillis";
    private static final String MAX_REQUESTS_KEY = "maxConcurrentRequests";
    private static final String MIN_WORD_SCORE = "minWordScore";
    public static final String HUGGING_FACE_MODEL = "huggingFaceModel";
    public static final String WHISPER_MODEL = "whisperModel";
    public static final String WAV2VEC2_SERVICE = "wav2vec2Service";
    public static final String REMOTE_SERVICE = "remoteServiceAddress";
    private static final String GOOGLE_MODEL = "googleModel";
    private static final String LANG_AUTO_VAL = "auto";
    private static final String SKIP_KNOWN_FILES = "skipKnownFiles";
    private static final String PRECISION = "precision";
    private static final String BATCH_SIZE = "batchSize";
    private static final String DEVICE = "device";

    private List<String> languages = new ArrayList<>();
    private List<String> mimesToProcess = new ArrayList<>();
    private String className;
    private String serviceRegion;
    private int minTimeout = 180; // seconds
    private int timeoutPerSec = 3; // seconds
    private String convertCmd;
    private int requestIntervalMillis = 0;
    private int maxConcurrentRequests;
    private float minWordScore = 0.7f;
    private String huggingFaceModel;
    private String whisperModel;
    private String remoteService;
    private String googleModel;
    private boolean skipKnownFiles = true;
    private String precision = "int8";
    private int batchSize = 1;
    private String device = "cpu";

    public String getDevice() {
        return device;
    }

    public String getPrecision() {
        return precision;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public boolean getSkipKnownFiles() {
        return this.skipKnownFiles;
    }

    public String getServiceRegion() {
        return serviceRegion;
    }

    public int getRequestIntervalMillis() {
        return requestIntervalMillis;
    }

    public int getMaxConcurrentRequests() {
        return maxConcurrentRequests;
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

    public void setClassName(String clazz) {
        this.className = clazz;
    }

    public int getTimeoutPerSec() {
        return timeoutPerSec;
    }

    public int getMinTimeout() {
        return minTimeout;
    }

    public String getConvertCmd() {
        return convertCmd;
    }

    public float getMinWordScore() {
        return minWordScore;
    }

    @Override
    public String getTaskEnableProperty() {
        return ENABLE_KEY;
    }

    @Override
    public String getTaskConfigFileName() {
        return CONF_FILE;
    }

    public String getHuggingFaceModel() {
        return huggingFaceModel;
    }

    public String getWhisperModel() {
        return whisperModel;
    }

    public String getRemoteService() {
        return remoteService;
    }

    public String getGoogleModel() {
        return googleModel;
    }

    @Override
    public void processProperties(UTF8Properties properties) {

        String langs = properties.getProperty(LANG_KEY).trim();
        if (LANG_AUTO_VAL.equalsIgnoreCase(langs)) {
            languages.add(System.getProperty(iped.localization.Messages.LOCALE_SYS_PROP));
        } else {
            for (String lang : langs.split(";")) {
                languages.add(lang.trim());
            }
        }

        String mimes = properties.getProperty(MIMES_KEY).trim();
        for (String mime : mimes.split(";")) {
            mimesToProcess.add(mime.trim());
        }

        className = properties.getProperty(IMPL_CLASS_KEY).trim();
        serviceRegion = properties.getProperty(REGION_KEY).trim();
        convertCmd = properties.getProperty(CONVERT_CMD_KEY).trim();
        requestIntervalMillis = Integer.valueOf(properties.getProperty(REQUEST_INTERVAL_KEY).trim());
        maxConcurrentRequests = Integer.valueOf(properties.getProperty(MAX_REQUESTS_KEY).trim());
        minWordScore = Float.valueOf(properties.getProperty(MIN_WORD_SCORE).trim());
        huggingFaceModel = properties.getProperty(HUGGING_FACE_MODEL);
        if (huggingFaceModel != null) {
            huggingFaceModel = huggingFaceModel.trim();
        }
        whisperModel = properties.getProperty(WHISPER_MODEL);
        if (whisperModel != null) {
            whisperModel = whisperModel.strip();
        }

        remoteService = properties.getProperty(REMOTE_SERVICE);
        if (remoteService == null) {
            remoteService = properties.getProperty(WAV2VEC2_SERVICE);
        }
        if (remoteService != null) {
            remoteService = remoteService.trim();
        }
        googleModel = properties.getProperty(GOOGLE_MODEL);
        if (googleModel != null) {
            googleModel = googleModel.trim();
        }

        String skipKnown = properties.getProperty(SKIP_KNOWN_FILES);
        if (skipKnown != null) {
            this.skipKnownFiles = Boolean.valueOf(skipKnown.trim());
        }
        String value = properties.getProperty(MIN_TIMEOUT_KEY);
        if (value != null) {
            minTimeout = Integer.valueOf(value.trim());
        }
        value = properties.getProperty(TIMEOUT_PER_SEC_KEY);
        if (value != null) {
            timeoutPerSec = Integer.valueOf(value.trim());
        }
        value = properties.getProperty(PRECISION);
        if (value != null) {
            precision = value.trim();
        }
        value = properties.getProperty(BATCH_SIZE);
        if (value != null) {
            batchSize = Integer.parseInt(value.trim());
        }

        value = properties.getProperty(DEVICE);
        if (value != null && !value.isBlank()) {
            device = value.strip();
        }
    }

    /**
     * Avoid leaking the transcription service address (host:port)
     * 
     * @param moduleOutput
     * @throws IOException
     */
    public void clearTranscriptionServiceAddress(File moduleOutput) throws IOException {
        File config = new File(moduleOutput, "conf/" + CONF_FILE);
        if (config.exists() && config.canWrite()) {
            String[] keys = { WAV2VEC2_SERVICE, REMOTE_SERVICE };
            List<String> lines = Files.readAllLines(config.toPath());
            List<String> outLines = new ArrayList<>();
            for (String line : lines) {
                for (String key : keys) {
                    if (!line.isEmpty() && (line.trim().startsWith(key) || line.substring(1).trim().startsWith(key))) {
                        line = "# " + key + " = 127.0.0.1:11111";
                        break;
                    }
                }
                outLines.add(line);
            }
            Files.write(config.toPath(), outLines);
        }
    }

}
