package iped.engine.task.transcript;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.grizzly.utils.Charsets;

import iped.engine.config.AudioTranscriptConfig;
import iped.engine.config.Configuration;
import iped.engine.config.ConfigurationManager;
import iped.exception.IPEDException;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.HardwareAbstractionLayer;

public class Wav2Vec2TranscriptTask extends AbstractTranscriptTask {

    private static Logger logger = LogManager.getLogger(Wav2Vec2TranscriptTask.class);

    private static final String SCRIPT_PATH = "/conf/scripts/Wav2Vec2Process.py";
    private static final String TRANSCRIPTION_FINISHED = "transcription_finished";
    private static final String MODEL_LOADED = "wav2vec2_model_loaded";
    private static final String HUGGINGSOUND_LOADED = "huggingsound_loaded";
    private static final String TERMINATE = "terminate_process";
    private static final String PING = "ping";

    private static final int MAX_TRANSCRIPTIONS = 1000;
    private static final byte[] NEW_LINE = "\n".getBytes();

    private static final int NUM_SERVERS = getNumProcessors();

    private static LinkedBlockingDeque<Server> deque = new LinkedBlockingDeque<>();

    private static volatile Level logLevel = Level.forName("MSG", 250);

    private static class Server {
        Process process;
        BufferedReader reader;
        int transcriptionsDone = 0;
    }

    protected static int getNumProcessors() {
        SystemInfo si = new SystemInfo();
        HardwareAbstractionLayer hal = si.getHardware();
        CentralProcessor cpu = hal.getProcessor();
        return cpu.getPhysicalPackageCount();
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {

        super.init(configurationManager);

        if (!this.isEnabled()) {
            return;
        }
        
        if (!deque.isEmpty())
            return;
        
        for (int i = 0; i < NUM_SERVERS; i++) {
            deque.add(startServer());
        }

        logLevel = Level.DEBUG;

    }

    private Server startServer() throws IOException {
        ProcessBuilder pb = new ProcessBuilder();
        String ipedRoot = Configuration.getInstance().appRoot;
        String python = SystemUtils.IS_OS_WINDOWS ? ipedRoot + "/python/python.exe" : "python3";
        String script = ipedRoot + SCRIPT_PATH;
        String model = super.transcriptConfig.getHuggingFaceModel();
        if (model == null) {
            throw new IPEDException("You must configure '" + AudioTranscriptConfig.HUGGING_FACE_MODEL
                    + "' in audio transcription config file.");
        }

        pb.command(python, script, model);

        Process process = pb.start();

        logInputStream(process.getErrorStream());

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        String line = reader.readLine();

        if (!HUGGINGSOUND_LOADED.equals(line)) {
            throw new IPEDException("'huggingsound' python lib not loaded correctly. Have you installed it?");
        }

        line = reader.readLine();

        if (!MODEL_LOADED.equals(line)) {
            throw new IPEDException("Error loading '" + model + "' transcription model.");
        }

        line = reader.readLine();

        logger.info("Model loaded on device={}", line);

        Server server = new Server();
        server.process = process;
        server.reader = reader;

        return server;
    }

    private void logInputStream(InputStream is) {
        Thread t = new Thread() {
            public void run() {
                byte[] buf = new byte[1024];
                int read = 0;
                try {
                    while ((read = is.read(buf)) != -1) {
                        logger.log(logLevel, new String(buf, 0, read).trim());
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void finish() throws Exception {
        super.finish();
        for (Server server : deque) {
            terminateServer(server);
        }
        deque.clear();
    }

    private void terminateServer(Server server) throws IOException, InterruptedException {
        Process process = server.process;
        process.getOutputStream().write(TERMINATE.getBytes(Charsets.UTF8_CHARSET));
        process.getOutputStream().write(NEW_LINE);
        process.getOutputStream().flush();
        process.waitFor(3, TimeUnit.SECONDS);
        if (process.isAlive()) {
            process.destroyForcibly();
        }
    }

    private boolean ping(Server server) throws IOException {
        server.process.getOutputStream().write(PING.getBytes(Charsets.UTF8_CHARSET));
        server.process.getOutputStream().write(NEW_LINE);
        server.process.getOutputStream().flush();
        return PING.equals(server.reader.readLine());
    }

    @Override
    protected TextAndScore transcribeAudio(File tmpFile) throws Exception {
        String path = evidence != null ? evidence.getPath() : tmpFile.getPath();
        return transcribeWavBreaking(tmpFile, path, f -> {
            try {
                return transcribeWavPart(f);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    protected TextAndScore transcribeWavPart(File tmpFile) throws Exception {

        TextAndScore textAndScore = null;

        Server server = deque.take();
        try {
            if (!ping(server) || server.transcriptionsDone >= MAX_TRANSCRIPTIONS) {
                terminateServer(server);
                server = startServer();
            }

            String filePath = tmpFile.getAbsolutePath().replace('\\', '/');
            server.process.getOutputStream().write(filePath.getBytes("UTF-8"));
            server.process.getOutputStream().write(NEW_LINE);
            server.process.getOutputStream().flush();

            String line;
            while (!TRANSCRIPTION_FINISHED.equals(line = server.reader.readLine().trim())) {
                logger.error("Unexpected error from transcription: {}", line);
            }

            Double score = Double.valueOf(server.reader.readLine().trim());
            String text = server.reader.readLine().trim();

            textAndScore = new TextAndScore();
            textAndScore.text = text;
            textAndScore.score = score;

            server.transcriptionsDone++;

        } finally {
            deque.add(server);
        }

        return textAndScore;
    }

}
