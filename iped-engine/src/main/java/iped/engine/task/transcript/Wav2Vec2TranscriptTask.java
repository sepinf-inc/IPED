package iped.engine.task.transcript;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang3.SystemUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.grizzly.utils.Charsets;

import iped.configuration.IConfigurationDirectory;
import iped.engine.config.AudioTranscriptConfig;
import iped.engine.config.Configuration;
import iped.engine.config.ConfigurationManager;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.HardwareAbstractionLayer;

public class Wav2Vec2TranscriptTask extends AbstractTranscriptTask {

    private static Logger logger = LogManager.getLogger(Wav2Vec2TranscriptTask.class);

    private static final String SCRIPT_PATH = "/scripts/tasks/Wav2Vec2Process.py";
    protected static final String TRANSCRIPTION_FINISHED = "transcription_finished";
    private static final String MODEL_LOADED = "wav2vec2_model_loaded";
    private static final String HUGGINGSOUND_LOADED = "huggingsound_loaded";
    private static final String TERMINATE = "terminate_process";
    private static final String PING = "ping";

    protected static final int MAX_TRANSCRIPTIONS = 100000;
    protected static final byte[] NEW_LINE = "\n".getBytes();

    protected static volatile Integer numProcesses;

    protected static LinkedBlockingDeque<Server> deque = new LinkedBlockingDeque<>();

    protected static volatile Level logLevel = Level.forName("MSG", 250);

    private static volatile AtomicBoolean init = new AtomicBoolean();
    
    static class Server {
        Process process;
        BufferedReader reader;
        int transcriptionsDone = 0;
        int device = 0;
    }

    protected static int getNumProcessors() {
        SystemInfo si = new SystemInfo();
        HardwareAbstractionLayer hal = si.getHardware();
        CentralProcessor cpu = hal.getProcessor();
        return cpu.getPhysicalPackageCount();
    }

    protected static int getNumConcurrentTranscriptions() {
        if (numProcesses == null) {
            throw new RuntimeException("'numProcesses' variable still not initialized");
        }
        return numProcesses;
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {

        super.init(configurationManager);

        if (!isEnabled()) {
            return;
        }
        
        String ipedRoot = System.getProperty(IConfigurationDirectory.IPED_ROOT);
        if (SystemUtils.IS_OS_WINDOWS && ipedRoot == null) {
            ipedRoot = Configuration.getInstance().appRoot;
            if (!new File(ipedRoot, "python/python.exe").exists()) {
                // Possibly generating report on a machine that have never run iped processing.
                this.transcriptConfig.setEnabled(false);
                logger.warn("Python.exe not found, disabling transcription module.");
                return;
            }
        }

        if (!deque.isEmpty())
            return;

        synchronized (init) {
            if (!init.get()) {
                try {
                    Server server;
                    int device = 0;
                    while ((server = startServer(device++)) != null) {
                        deque.add(server);
                    }

                } catch (Exception e) {
                    if (hasIpedDatasource()) {
                        transcriptConfig.setEnabled(false);
                        logger.warn("Could not initialize audio transcription. Task disabled.");
                    } else {
                        throw e;
                    }
                }
                init.set(true);
            }
        }

        logLevel = Level.DEBUG;

    }

    protected Server startServer(int device) throws StartupException {
        try {
            return startServer0(device);
        } catch (Exception e) {
            if (e instanceof StartupException) {
                throw (StartupException) e;
            } else {
                e.printStackTrace();
                throw new StartupException(e.toString());
            }
        }
    }

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
        String model = super.transcriptConfig.getHuggingFaceModel();
        if (model == null) {
            throw new StartupException("You must configure '" + AudioTranscriptConfig.HUGGING_FACE_MODEL
                    + "' in audio transcription config file.");
        }

        pb.command(python, script, model, Integer.toString(device));

        Process process = pb.start();

        logInputStream(process.getErrorStream());

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

        String line = reader.readLine();

        if (!HUGGINGSOUND_LOADED.equals(line)) {
            throw new StartupException("'huggingsound' python lib not loaded correctly. Have you installed it?");
        }

        int cudaCount = Integer.valueOf(reader.readLine());
        if (numProcesses == null) {
            int cpus = getNumProcessors();
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

    protected void logInputStream(InputStream is) {
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

    protected void terminateServer(Server server) throws InterruptedException {
        Process process = server.process;
        try {
            process.getOutputStream().write(TERMINATE.getBytes(Charsets.UTF8_CHARSET));
            process.getOutputStream().write(NEW_LINE);
            process.getOutputStream().flush();
        } catch (IOException e) {
            // ignore
        }
        if (!process.waitFor(3, TimeUnit.SECONDS)) {
            process.destroy();
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        }
    }

    protected boolean ping(Server server) {
        try {
            server.process.getOutputStream().write(PING.getBytes(Charsets.UTF8_CHARSET));
            server.process.getOutputStream().write(NEW_LINE);
            server.process.getOutputStream().flush();
            if (PING.equals(server.reader.readLine())) {
                return true;
            } else {
                throw new IOException("ping not returned fine");
            }
        } catch (IOException e) {
            logger.warn("Fail to ping transcription process pid={} exception={}", server.process.pid(), e.toString());
        }
        return false;
    }

    @Override
    protected TextAndScore transcribeAudio(File tmpFile) throws Exception {
        String path = evidence != null ? evidence.getPath() : tmpFile.getPath();
        return transcribeWavBreaking(tmpFile, path, f -> {
            try {
                return transcribeWavPart(f);
            } catch (Exception e) {
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
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
                server = startServer(server.device);
            }

            String filePath = tmpFile.getAbsolutePath().replace('\\', '/');
            server.process.getOutputStream().write(filePath.getBytes("UTF-8"));
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

            Double score = Double.valueOf(server.reader.readLine());
            String text = server.reader.readLine();

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
