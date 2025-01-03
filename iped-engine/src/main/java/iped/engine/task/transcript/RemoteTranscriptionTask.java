package iped.engine.task.transcript;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tika.io.TemporaryResources;

import iped.configuration.IConfigurationDirectory;
import iped.data.IItem;
import iped.engine.config.AudioTranscriptConfig;
import iped.engine.config.ConfigurationManager;
import iped.engine.core.Manager;
import iped.engine.io.TimeoutException;
import iped.engine.task.transcript.RemoteTranscriptionService.MESSAGES;
import iped.exception.IPEDException;

public class RemoteTranscriptionTask extends AbstractTranscriptTask {

    private static Logger logger = LogManager.getLogger(Wav2Vec2TranscriptTask.class);

    private static final int MAX_CONNECT_ERRORS = 60;

    private static final int UPDATE_SERVERS_INTERVAL_MILLIS = 60000;

    private static List<Server> servers = new ArrayList<>();

    private static int currentServer = -1;

    private static AtomicInteger numConnectErrors = new AtomicInteger();

    private static AtomicLong audioSendingTime = new AtomicLong();

    private static AtomicLong transcriptReceiveTime = new AtomicLong();

    private static AtomicBoolean statsPrinted = new AtomicBoolean();

    private static volatile AtomicBoolean init = new AtomicBoolean();

    private static long lastUpdateServersTime = 0;

    private static class Server {

        String ip;
        int port;

        public String toString() {
            return ip + ":" + port;
        }
    }

    // See https://github.com/sepinf-inc/IPED/issues/1576
    private int getRetryIntervalMillis() {
        // This depends on how much time worker nodes need to consume their queue.
        // Of course audios duration, nodes queue size and performance affect this.
        // This tries to be fair with clients independent of their number of threads.
        return Manager.getInstance().getNumWorkers() * 100;
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {

        super.init(configurationManager);

        if (!isEnabled()) {
            return;
        }
        
        if (!servers.isEmpty()) {
            return;
        }

        boolean disable = false;
        if (transcriptConfig.getRemoteService() == null) {
            String ipedRoot = System.getProperty(IConfigurationDirectory.IPED_ROOT);
            if (ipedRoot != null) {
                Path path = new File(ipedRoot, "conf/" + AudioTranscriptConfig.CONF_FILE).toPath();
                configurationManager.getConfigurationDirectory().addPath(path);
                configurationManager.addObject(transcriptConfig);
                configurationManager.loadConfig(transcriptConfig);
                // maybe user changed installation configs
                if (transcriptConfig.getRemoteService() == null) {
                    disable = true;
                } else {
                    transcriptConfig.setEnabled(true);
                    transcriptConfig.setClassName(this.getClass().getName());
                }
            } else {
                disable = true;
            }
        }
        
        if (disable) {
            transcriptConfig.setEnabled(false);
            logger.warn("Remote transcription module disabled, service address not configured.");
            return;
        }

        synchronized (init) {
            if (!init.get()) {
                try {
                    requestServers(true);
                } catch (Exception e) {
                    if (hasIpedDatasource()) {
                        transcriptConfig.setEnabled(false);
                        logger.warn("Could not initialize remote transcription. Task disabled.");
                    } else {
                        throw e;
                    }
                }
                init.set(true);
            }
        }
    }

    private static synchronized void requestServers(RemoteTranscriptionTask task, boolean now) throws IOException {
        if (!now && System.currentTimeMillis() - lastUpdateServersTime < UPDATE_SERVERS_INTERVAL_MILLIS) {
            return;
        }
        String[] ipAndPort = task.transcriptConfig.getRemoteService().split(":");
        String ip = ipAndPort[0];
        int port = Integer.parseInt(ipAndPort[1]);
        try (Socket client = new Socket(ip, port);
                InputStream is = client.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), true)) {

            client.setSoTimeout(10000);
            writer.println(MESSAGES.DISCOVER);
            int numServers = Integer.parseInt(reader.readLine());
            List<Server> servers = new ArrayList<>();
            for (int i = 0; i < numServers; i++) {
                String[] ipPort = reader.readLine().split(":");
                Server server = new Server();
                server.ip = ipPort[0];
                server.port = Integer.parseInt(ipPort[1]);
                servers.add(server);
                logger.info("Transcription server discovered: {}:{}", server.ip, server.port);
            }
            RemoteTranscriptionTask.servers = servers;
            lastUpdateServersTime = System.currentTimeMillis();
        } catch (ConnectException e) {
            String msg = "Central transcription node refused connection, is it online? " + e.toString();
            if (servers.isEmpty()) {
                throw new IPEDException(msg);
            } else {
                logger.warn(msg);
            }
        }
    }

    private void requestServers(boolean now) throws IOException {
        requestServers(this, now);
    }


    @Override
    public void finish() throws Exception {
        super.finish();
        if (isEnabled() && !statsPrinted.getAndSet(true)) {
            int numWorkers = this.worker.manager.getNumWorkers();
            DecimalFormat df = new DecimalFormat();
            logger.info("Time spent to send audios: {}s", df.format(audioSendingTime.get() / (1000 * numWorkers)));
            logger.info("Time spent to receive transcriptions: {}s", df.format(transcriptReceiveTime.get() / (1000 * numWorkers)));
        }
    }

    /**
     * Returns a transcription server between the discovered ones using a simple
     * circular approach.
     * 
     * @return Server instance to use
     */
    private static synchronized Server getServer() {
        if (servers.isEmpty()) {
            throw new IPEDException("No transcription server available!");
        }
        currentServer++;
        if (currentServer >= servers.size()) {
            currentServer = 0;
        }
        return servers.get(currentServer);
    }

    /**
     * Don't convert to WAV on client side, return the audio as is.
     */
    @Override
    protected File getTempFileToTranscript(IItem evidence, TemporaryResources tmp) throws IOException, InterruptedException {
        return evidence.getTempFile();
    }

    @Override
    protected TextAndScore transcribeAudio(File tmpFile) throws Exception {

        while (true) {
            requestServers(false);
            Server server = getServer();
            long requestTime = System.currentTimeMillis();
            try (Socket serverSocket = new Socket(server.ip, server.port);
                    InputStream is = serverSocket.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                    BufferedOutputStream bos = new BufferedOutputStream(serverSocket.getOutputStream())) {

                numConnectErrors.set(0);
                
                int timeoutSecs = (int) (MIN_TIMEOUT + TIMEOUT_PER_MB * tmpFile.length() / (1 << 20));
                serverSocket.setSoTimeout(1000 * timeoutSecs);

                String response = reader.readLine();
                if (response == null || MESSAGES.BUSY.toString().equals(response)) {
                    logger.debug("Transcription server {} busy, trying another one.", server);
                    sleepBeforeRetry(requestTime);
                    continue;
                }
                if (!MESSAGES.ACCEPTED.toString().equals(response)) {
                    logger.error("Error 0 in communication with {}. The audio will be retried.", server);
                    continue;
                }

                logger.debug("Transcription server {} accepted connection", server);

                long t0 = System.currentTimeMillis();

                bos.write(MESSAGES.VERSION_1_2.toString().getBytes());
                // bos.write("\n".getBytes());

                bos.write(MESSAGES.AUDIO_SIZE.toString().getBytes());

                DataOutputStream dos = new DataOutputStream(bos);
                // Must use long see #1833
                dos.writeLong(tmpFile.length());
                dos.flush();

                Files.copy(tmpFile.toPath(), bos);
                bos.flush();

                long t1 = System.currentTimeMillis();

                response = reader.readLine();

                while (MESSAGES.PING.toString().equals(response)) {
                    logger.debug("ping {}", response);
                    response = reader.readLine();
                }

                if (MESSAGES.WARN.toString().equals(response)) {
                    String warn = reader.readLine();
                    boolean tryAgain = false;
                    if (warn.contains(TimeoutException.class.getName())) {
                        // Timeout converting audio to wav, possibly it's corrupted
                        evidence.setTimeOut(true);
                        stats.incTimeouts();
                    } else if (warn.contains(SocketTimeoutException.class.getName()) || warn.contains(SocketException.class.getName())) {
                        tryAgain = true;
                    }
                    logger.warn("Fail to transcribe on server: {} audio: {} error: {}.{}", server, evidence.getPath(), warn, (tryAgain ? " The audio will be retried." : ""));
                    if (tryAgain) {
                        continue;
                    }
                    return null;
                }
                if (MESSAGES.ERROR.toString().equals(response) || response == null) {
                    String error = response != null ? reader.readLine() : "Remote server process crashed or node was turned off!";
                    logger.error("Error 1 in communication with {}: {}. The audio will be retried.", server, error);
                    throw new SocketException(error);
                }

                TextAndScore textAndScore = new TextAndScore();
                textAndScore.score = Double.parseDouble(response);
                textAndScore.text = reader.readLine();

                long t2 = System.currentTimeMillis();

                if (!MESSAGES.DONE.toString().equals(reader.readLine())) {
                    logger.error("Error 2 in communication with {}. The audio will be retried.", server);
                    throw new SocketException("Error receiving transcription.");
                }

                audioSendingTime.addAndGet(t1 - t0);
                transcriptReceiveTime.addAndGet(t2 - t1);

                return textAndScore;

            } catch (SocketTimeoutException | SocketException e) {
                if (e instanceof ConnectException) {
                    numConnectErrors.incrementAndGet();
                    if (numConnectErrors.get() / this.worker.manager.getNumWorkers() >= MAX_CONNECT_ERRORS) {
                        throw new TooManyConnectException();
                    }
                    sleepBeforeRetry(requestTime);
                    requestServers(true);
                } else {
                    logger.warn("Network error communicating to server: " + server + ", retrying audio: " + evidence.getPath(), e);
                }
            }
        }

    }

    private void sleepBeforeRetry(long lastRequestTime) {
        long sleep = getRetryIntervalMillis() - (System.currentTimeMillis() - lastRequestTime);
        if (sleep > 0) {
            try {
                Thread.sleep(sleep);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

}
