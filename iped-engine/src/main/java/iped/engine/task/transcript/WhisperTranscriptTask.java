package iped.engine.task.transcript;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
    protected Server startServer0(int deviceId) throws IOException {
        if (numProcesses != null && deviceId == numProcesses) {
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
        String device = transcriptConfig.getDevice();

        pb.command(python, script, model, device, Integer.toString(deviceId), Integer.toString(threads), lang, precision, batchSize);

        Process process = pb.start();

        logInputStream(process.getErrorStream());

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));

        String line = reader.readLine();

        if (!LIBRARY_LOADED.equals(line)) {
            throw new StartupException("Neither 'faster_whisper' nor 'whisperx' python libraries were loaded correctly. You need to install one of them!");
        }

        line = reader.readLine();
        logger.info("Transcription library loaded: {}", line);

        if ("whisperx".equals(line) && !ffmpegFound) {
            throw new IPEDException("FFmpeg not found on PATH, it is needed by WhisperX python library.");
        }
        line = reader.readLine();
        if (line == null) {
            throw new StartupException("Error getting the number of cuda devices.");
        }

        int cudaCount = 0;
        try {
            cudaCount = Integer.valueOf(line);
        } catch (NumberFormatException e) {
            throw new StartupException("Error converting the number of cuda devices: " + line);
        }
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
        server.device = deviceId;

        return server;
    }

    @Override
    protected TextAndScore transcribeAudio(File tmpFile) throws Exception {
        return transcribeWavPart(tmpFile);
    }

    protected List<TextAndScore> transcribeAudios(ArrayList<File> tmpFiles) throws Exception {

        ArrayList<TextAndScore> textAndScores = new ArrayList<>();
        for (int i = 0; i < tmpFiles.size(); i++) {
            textAndScores.add(null);
        }

        Server server = deque.take();
        try {
            if (!ping(server) || server.transcriptionsDone >= MAX_TRANSCRIPTIONS) {
                terminateServer(server);
                server = startServer(server.device);
            }

            StringBuilder filePaths = new StringBuilder();
            for (int i = 0; i < tmpFiles.size(); i++) {
                if (i > 0) {
                    filePaths.append(",");
                }
                filePaths.append(tmpFiles.get(i).getAbsolutePath().replace('\\', '/'));

            }
            server.process.getOutputStream().write(filePaths.toString().getBytes("UTF-8"));
            server.process.getOutputStream().write(NEW_LINE);
            server.process.getOutputStream().flush();

            String line;
            while (!TRANSCRIPTION_FINISHED.equals(line = server.reader.readLine())) {
                if (line == null) {
                    throw new ProcessCrashedException();
                } else {
                    throw new RuntimeException("Transcription failed, returned: " + line);
                }
            }
            for (int i = 0; i < tmpFiles.size(); i++) {
                Double score = Double.valueOf(server.reader.readLine());
                String text = server.reader.readLine();

                TextAndScore textAndScore = new TextAndScore();
                textAndScore.text = text;
                textAndScore.score = score;
                textAndScores.set(i, textAndScore);
                server.transcriptionsDone++;
            }

        } finally {
            deque.add(server);
        }

        return textAndScores;
    }

    @Override
    protected void logInputStream(InputStream is) {
        List<String> ignoreMsgs = Arrays.asList("With dispatcher enabled, this function is no-op. You can remove the function call.", "torchvision is not available - cannot save figures",
                "Lightning automatically upgraded your loaded checkpoint from", "Model was trained with pyannote.audio 0.0.1, yours is", "Model was trained with torch 1.10.0+cu102, yours is");
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
