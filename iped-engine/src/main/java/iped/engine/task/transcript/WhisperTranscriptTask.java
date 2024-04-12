package iped.engine.task.transcript;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import iped.configuration.IConfigurationDirectory;
import iped.engine.config.AudioTranscriptConfig;
import iped.engine.config.Configuration;

public class WhisperTranscriptTask extends Wav2Vec2TranscriptTask {

    private static Logger logger = LogManager.getLogger(Wav2Vec2TranscriptTask.class);

    private static final String SCRIPT_PATH = "/scripts/tasks/WhisperProcess.py";
    private static final String LIBRARY_LOADED = "library_loaded";
    private static final String MODEL_LOADED = "model_loaded";

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

        pb.command(python, script, model, Integer.toString(device), Integer.toString(threads), transcriptConfig.getLanguages().get(0));

        Process process = pb.start();

        logInputStream(process.getErrorStream());

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        String line = reader.readLine();

        if (!LIBRARY_LOADED.equals(line)) {
            throw new StartupException("'faster_whisper' python lib not loaded correctly. Have you installed it?");
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

}
