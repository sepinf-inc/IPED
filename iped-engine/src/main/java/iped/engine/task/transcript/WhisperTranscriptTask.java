package iped.engine.task.transcript;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import iped.configuration.IConfigurationDirectory;
import iped.engine.config.AudioTranscriptConfig;
import iped.engine.config.Configuration;
import iped.engine.config.ConfigurationManager;
import iped.exception.IPEDException;

public class WhisperTranscriptTask extends Wav2Vec2TranscriptTask {

    private static Logger logger = LogManager.getLogger(Wav2Vec2TranscriptTask.class);

    private static final String SCRIPT_PATH = "/scripts/tasks/WhisperProcess.py";
    private static final String LIBRARY_LOADED = "library_loaded";
    private static final String MODEL_LOADED = "model_loaded";

    private static final AtomicBoolean ffmpegTested = new AtomicBoolean();
    private static volatile boolean ffmpegFound;

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {
        if (!ffmpegTested.getAndSet(true)) {
            try {
                Runtime.getRuntime().exec("ffmpeg");
                ffmpegFound = true;
            } catch (IOException e) {
                ffmpegFound = false;
            }
        }
        super.init(configurationManager);
    }

    @Override
    protected Server startServer0(int device) throws IOException {
        if (numProcesses != null && device == numProcesses) {
            return null;
        }
        ProcessBuilder pb = new ProcessBuilder();
        String ipedRoot = System.getProperty(IConfigurationDirectory.IPED_ROOT);
        if (ipedRoot == null) {
            ipedRoot = Configuration.getInstance().appRoot;
        }
        String python = SystemUtils.IS_OS_WINDOWS ? ipedRoot + "/python/python.exe" : "python3";
        String script = ipedRoot + SCRIPT_PATH;
        String model = super.transcriptConfig.getWhisperModel();
        if (model == null) {
            throw new StartupException("You must configure '" + AudioTranscriptConfig.WHISPER_MODEL + "' in audio transcription config file.");
        }

        int cpus = getNumProcessors();
        int threads = Runtime.getRuntime().availableProcessors() / cpus;

        String lang = transcriptConfig.getLanguages().get(0);
        if (lang.contains("-")) {
            lang = lang.substring(0, lang.indexOf("-"));
        }

        String precision = transcriptConfig.getPrecision();
        String batchSize = Integer.toString(transcriptConfig.getBatchSize());

        pb.command(python, script, model, Integer.toString(device), Integer.toString(threads), lang, precision, batchSize);

        Process process = pb.start();

        logInputStream(process.getErrorStream());

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        String line = reader.readLine();

        if (!LIBRARY_LOADED.equals(line)) {
            throw new StartupException("Neither 'faster_whisper' nor 'whisperx' python libraries were loaded correctly. You need to install one of them!");
        }

        line = reader.readLine();
        logger.info("Transcription library loaded: {}", line);

        if ("whisperx".equals(line) && !ffmpegFound) {
            throw new IPEDException("FFmpeg not found on PATH, it is needed by WhisperX python library.");
        }

        int cudaCount = Integer.valueOf(reader.readLine());
        if (numProcesses == null) {
            logger.info("Number of CUDA devices detected: {}", cudaCount);
            logger.info("Number of CPU devices detected: {}", cpus);
            if (cudaCount > 0) {
                numProcesses = cudaCount;
            } else {
                numProcesses = cpus;
            }
        }

        String msgToIgnore = "Ignored unknown";
        while ((line = reader.readLine()) != null && line.startsWith(msgToIgnore))
            ;

        if (!MODEL_LOADED.equals(line)) {
            throw new StartupException("Error loading '" + model + "' transcription model.");
        }

        line = reader.readLine();

        logger.info("Model loaded on device={}", line);

        Server server = new Server();
        server.process = process;
        server.reader = reader;
        server.device = device;

        return server;
    }

    @Override
    protected TextAndScore transcribeAudio(File tmpFile) throws Exception {
        return transcribeWavPart(tmpFile);
    }

    @Override
    protected void logInputStream(InputStream is) {
        List<String> ignoreMsgs = Arrays.asList(
                "With dispatcher enabled, this function is no-op. You can remove the function call.",
                "torchvision is not available - cannot save figures",
                "Lightning automatically upgraded your loaded checkpoint from",
                "Model was trained with pyannote.audio 0.0.1, yours is",
                "Model was trained with torch 1.10.0+cu102, yours is");
        Thread t = new Thread() {
            public void run() {
                byte[] buf = new byte[1024];
                int read = 0;
                try {
                    while ((read = is.read(buf)) != -1) {
                        String msg = new String(buf, 0, read).trim();
                        boolean ignore = false;
                        for (String i : ignoreMsgs) {
                            if (msg.contains(i)) {
                                ignore = true;
                                break;
                            }
                        }
                        if (ignore) {
                            logger.warn(msg);
                        } else {
                            logger.log(logLevel, msg);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        t.setDaemon(true);
        t.start();
    }

}
