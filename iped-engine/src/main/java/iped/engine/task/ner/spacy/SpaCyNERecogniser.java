package iped.engine.task.ner.spacy;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.lang3.SystemUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.configuration.IConfigurationDirectory;
import iped.engine.config.Configuration;
import iped.engine.config.NamedEntityTaskConfig;
import iped.engine.task.ner.INamedEntityRecognizer;

/**
 * Named Entity Recognition implementation backed by SpaCy running in external Python processes.
 */
public class SpaCyNERecogniser implements INamedEntityRecognizer {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpaCyNERecogniser.class);

    private static final String SCRIPT_RELATIVE_PATH = "scripts/tasks/SpaCyProcess.py";
    private static final String SPACY_LOADED = "spacy_loaded";
    private static final String MODEL_LOADED = "model_loaded";
    private static final String PING = "ping";
    private static final String TERMINATE = "terminate_process";
    private static final long QUEUE_POLL_TIMEOUT_SECONDS = 60;

    private final BlockingQueue<SpaCyServer> serverQueue = new LinkedBlockingQueue<>();
    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private volatile boolean isAvailable = false;
    private NamedEntityTaskConfig taskConfig;
    private String resolvedPythonPath;
    private String resolvedScriptPath;
    private String modelsArg;

    private static class SpaCyServer {
        Process process;
        BufferedReader reader;
        BufferedWriter writer;
        volatile boolean alive = true;
    }

    @Override
    public void init(NamedEntityTaskConfig config) throws Exception {
        this.taskConfig = config;
        if (isInitialized.getAndSet(true)) {
            return;
        }

        resolvePythonBinary(config);
        if (this.resolvedScriptPath == null) {
            try {
                resolveScriptPath();
            } catch (IOException e) {
                LOGGER.warn("Could not locate SpaCyProcess.py script: {}. Disabling task.", e.getMessage());
                config.setEnabled(false);
                return;
            }
        }

        // Build models argument string: "default:en_core_web_sm,pt:pt_core_news_sm"
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : config.getLangToModelMap().entrySet()) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(entry.getKey()).append(":").append(entry.getValue());
        }
        this.modelsArg = sb.toString();

        if (this.modelsArg.isEmpty()) {
            LOGGER.warn("No language models configured for SpaCy NER. Disabling task.");
            config.setEnabled(false);
            return;
        }

        int desiredProcesses = config.getNumProcesses();
        if (desiredProcesses <= 0) {
            desiredProcesses = 1;
        }

        for (int i = 0; i < desiredProcesses; i++) {
            SpaCyServer server = startServer();
            if (server != null) {
                serverQueue.add(server);
            } else {
                // If the first process fails to start, disable the task
                if (serverQueue.isEmpty()) {
                    LOGGER.warn("Failed to initialize SpaCy NER process. Disabling task.");
                    config.setEnabled(false);
                    return;
                }
            }
        }

        isAvailable = !serverQueue.isEmpty();
        if (isAvailable) {
            LOGGER.info("SpaCy NER successfully initialized with {} worker process(es). Models: {}",
                    serverQueue.size(), modelsArg);
        }
    }

    private void resolvePythonBinary(NamedEntityTaskConfig config) {
        if (config.getPythonPath() != null && !config.getPythonPath().isEmpty()) {
            this.resolvedPythonPath = config.getPythonPath();
            return;
        }

        String ipedRoot = System.getProperty(IConfigurationDirectory.IPED_ROOT);
        if (ipedRoot == null && Configuration.getInstance() != null) {
            ipedRoot = Configuration.getInstance().appRoot;
        }

        if (SystemUtils.IS_OS_WINDOWS) {
            if (ipedRoot != null) {
                File bundledPy = new File(ipedRoot, "python/python.exe");
                if (bundledPy.exists()) {
                    this.resolvedPythonPath = bundledPy.getAbsolutePath();
                    return;
                }
            }
            this.resolvedPythonPath = "python";
        } else {
            this.resolvedPythonPath = "python3";
        }
    }

    private void resolveScriptPath() throws IOException {
        String ipedRoot = System.getProperty(IConfigurationDirectory.IPED_ROOT);
        if (ipedRoot == null && Configuration.getInstance() != null) {
            ipedRoot = Configuration.getInstance().appRoot;
        }

        if (ipedRoot != null) {
            File f = new File(ipedRoot, SCRIPT_RELATIVE_PATH);
            if (f.exists()) {
                this.resolvedScriptPath = f.getAbsolutePath();
                return;
            }
        }

        // Check relative file system path from working directory or parent directories
        File[] candidateLocations = new File[] {
            new File("iped-app/resources/" + SCRIPT_RELATIVE_PATH),
            new File("../iped-app/resources/" + SCRIPT_RELATIVE_PATH),
            new File("../../iped-app/resources/" + SCRIPT_RELATIVE_PATH)
        };
        for (File candidate : candidateLocations) {
            if (candidate.exists()) {
                this.resolvedScriptPath = candidate.getAbsolutePath();
                return;
            }
        }

        // Fallback: check classpath resource and copy to temporary file if necessary
        InputStream is = getClass().getResourceAsStream("/" + SCRIPT_RELATIVE_PATH);
        if (is == null) {
            is = getClass().getResourceAsStream("/SpaCyProcess.py");
        }
        if (is != null) {
            try (InputStream in = is) {
                File tempScript = File.createTempFile("SpaCyProcess", ".py");
                tempScript.deleteOnExit();
                Files.copy(in, tempScript.toPath(), StandardCopyOption.REPLACE_EXISTING);
                this.resolvedScriptPath = tempScript.getAbsolutePath();
                return;
            }
        }

        throw new IOException("Could not locate SpaCyProcess.py script.");
    }

    private SpaCyServer startServer() {
        ProcessBuilder pb = new ProcessBuilder(resolvedPythonPath, resolvedScriptPath, modelsArg);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);

        Process process = null;
        BufferedReader reader = null;
        BufferedWriter writer = null;
        try {
            process = pb.start();
            reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));

            String line = reader.readLine();
            if (line == null || !line.equals(SPACY_LOADED)) {
                if (line != null && line.startsWith("ERROR_IMPORT")) {
                    LOGGER.warn("SpaCy library not found in Python environment ('{}'). "
                            + "Please run: pip install spacy && python -m spacy download pt_core_news_sm",
                            resolvedPythonPath);
                } else {
                    LOGGER.warn("Unexpected response starting SpaCy process: {}", line);
                }
                closeQuietly(writer);
                closeQuietly(reader);
                destroyProcess(process);
                return null;
            }

            line = reader.readLine();
            if (line == null || !line.equals(MODEL_LOADED)) {
                LOGGER.warn("Failed to load SpaCy models ('{}'): {}", modelsArg, line);
                closeQuietly(writer);
                closeQuietly(reader);
                destroyProcess(process);
                return null;
            }

            SpaCyServer server = new SpaCyServer();
            server.process = process;
            server.reader = reader;
            server.writer = writer;
            return server;

        } catch (Exception e) {
            LOGGER.warn("Error starting SpaCy process: {}", e.getMessage());
            closeQuietly(writer);
            closeQuietly(reader);
            if (process != null) {
                destroyProcess(process);
            }
            return null;
        }
    }

    @Override
    public boolean isAvailable() {
        return isAvailable;
    }

    @Override
    public Map<String, Set<String>> recognize(String text, String lang) throws Exception {
        if (!isAvailable || text == null || text.trim().isEmpty()) {
            return Map.of();
        }

        SpaCyServer server;
        try {
            server = serverQueue.poll(QUEUE_POLL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Map.of();
        }

        if (server == null) {
            LOGGER.warn("Timed out waiting for available SpaCy worker process. Skipping NER for this text fragment.");
            return Map.of();
        }

        try {
            String b64Text = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
            server.writer.write("RECOGNIZE " + lang + " " + b64Text + "\n");
            server.writer.flush();

            String responseLine = server.reader.readLine();
            if (responseLine == null) {
                // Process died
                LOGGER.warn("SpaCy worker process terminated unexpectedly.");
                destroyServer(server);
                server = replaceServer();
                return Map.of();
            }

            return parseJsonResponse(responseLine);

        } catch (IOException e) {
            LOGGER.warn("I/O error communicating with SpaCy worker process: {}", e.getMessage());
            if (server != null) {
                destroyServer(server);
                server = replaceServer();
            }
            throw e;

        } finally {
            if (server != null && server.alive) {
                if (isAvailable) {
                    serverQueue.offer(server);
                } else {
                    destroyServer(server);
                }
            }
        }
    }

    private SpaCyServer replaceServer() {
        if (!isAvailable) {
            return null;
        }
        LOGGER.warn("Attempting to spawn replacement SpaCy worker process...");
        try {
            return startServer();
        } catch (Exception e) {
            LOGGER.error("Failed to spawn replacement SpaCy worker process: {}", e.getMessage(), e);
            return null;
        }
    }

    private Map<String, Set<String>> parseJsonResponse(String jsonStr) {
        Map<String, Set<String>> result = new HashMap<>();
        try {
            JSONParser parser = new JSONParser();
            Object parsed = parser.parse(jsonStr);
            if (parsed instanceof JSONObject) {
                JSONObject obj = (JSONObject) parsed;
                for (Object keyObj : obj.keySet()) {
                    String category = (String) keyObj;
                    Object valObj = obj.get(category);
                    if (valObj instanceof JSONArray) {
                        JSONArray arr = (JSONArray) valObj;
                        Set<String> set = result.computeIfAbsent(category, k -> new HashSet<>());
                        for (Object item : arr) {
                            if (item != null) {
                                set.add(item.toString());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Error parsing SpaCy JSON response: {}", jsonStr, e);
        }
        return result;
    }

    void setScriptPath(String scriptPath) {
        this.resolvedScriptPath = scriptPath;
    }

    @Override
    public void finish() {
        isAvailable = false;
        isInitialized.set(false);
        SpaCyServer server;
        while ((server = serverQueue.poll()) != null) {
            destroyServer(server);
        }
    }

    private void destroyServer(SpaCyServer server) {
        if (server == null) {
            return;
        }
        server.alive = false;
        if (server.writer != null) {
            try {
                server.writer.write(TERMINATE + "\n");
                server.writer.flush();
            } catch (Exception ignored) {
            }
        }
        closeQuietly(server.writer);
        closeQuietly(server.reader);
        destroyProcess(server.process);
    }

    private static void closeQuietly(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception ignored) {
            }
        }
    }

    private void destroyProcess(Process process) {
        if (process == null) {
            return;
        }
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroy();
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }
}
