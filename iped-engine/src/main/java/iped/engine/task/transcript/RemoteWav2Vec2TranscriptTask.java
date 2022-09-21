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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tika.io.TemporaryResources;

import iped.data.IItem;
import iped.engine.config.ConfigurationManager;
import iped.engine.io.TimeoutException;
import iped.engine.task.transcript.RemoteWav2Vec2Service.MESSAGES;
import iped.exception.IPEDException;

public class RemoteWav2Vec2TranscriptTask extends AbstractTranscriptTask {

    private static Logger logger = LogManager.getLogger(Wav2Vec2TranscriptTask.class);

    private static final int MAX_CONNECT_ERRORS = 60;

    private static final int RETRY_INTERVAL_MILLIS = 100;

    private static final int UPDATE_SERVERS_INTERVAL_MILLIS = 60000;

    private static List<Server> servers = new ArrayList<>();

    private static int currentServer = -1;

    private static AtomicInteger numConnectErrors = new AtomicInteger();

    private static long lastUpdateServersTime = 0;

    private static class Server {

        String ip;
        int port;

        public String toString() {
            return ip + ":" + port;
        }
    }

    @Override
    public void init(ConfigurationManager configurationManager) throws Exception {

        super.init(configurationManager);

        if (!this.isEnabled()) {
            return;
        }
        
        if (!servers.isEmpty()) {
            return;
        }

        requestServers(true);
        
    }

    private static synchronized void requestServers(RemoteWav2Vec2TranscriptTask task, boolean now) throws IOException {
        if (!now && System.currentTimeMillis() - lastUpdateServersTime < UPDATE_SERVERS_INTERVAL_MILLIS) {
            return;
        }
        String[] ipAndPort = task.transcriptConfig.getWav2vec2Service().split(":");
        String ip = ipAndPort[0];
        int port = Integer.parseInt(ipAndPort[1]);
        try (Socket client = new Socket(ip, port);
                InputStream is = client.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                PrintWriter writer = new PrintWriter(
                        new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), true)) {

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
            if (!servers.isEmpty()) {
                RemoteWav2Vec2TranscriptTask.servers = servers;
                lastUpdateServersTime = System.currentTimeMillis();
            }
        } catch (ConnectException e) {
            if (servers.isEmpty()) {
                throw new IPEDException("Transcription server refused connection, is it online?");
            } else {
                logger.error("Transcription server refused connection, is it online?");
            }
        }
    }

    private void requestServers(boolean now) throws IOException {
        requestServers(this, now);
    }


    @Override
    public void finish() throws Exception {
        super.finish();
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

    @Override
    protected File getTempFileToTranscript(IItem evidence, TemporaryResources tmp)
            throws IOException, InterruptedException {
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
                    logger.error("Error 0 in communication channel with {}.", server);
                    continue;
                }

                logger.info("Transcription server {} accepted connection", server);

                bos.write(MESSAGES.AUDIO_SIZE.toString().getBytes());

                DataOutputStream dos = new DataOutputStream(bos);
                // WAV part should be smaller than 1min, so smaller than 2GB
                dos.writeInt((int) tmpFile.length());
                dos.flush();

                Files.copy(tmpFile.toPath(), bos);
                bos.flush();

                response = reader.readLine();
                if (MESSAGES.WARN.toString().equals(response)) {
                    String warn = reader.readLine();
                    logger.warn("Fail to transcribe on server:{} audio:{} error:{}", server, evidence.getPath(), warn);
                    if (warn.contains(TimeoutException.class.getName())) {
                        evidence.setTimeOut(true);
                        stats.incTimeouts();
                    }
                    return null;
                }
                if (MESSAGES.ERROR.toString().equals(response) || response == null) {
                    String error = response != null ? reader.readLine() : "Remote server process possibly crashed!";
                    logger.error("Error 1 in communication channel with {}: {}", server, error);
                    throw new IOException(error);
                }

                TextAndScore textAndScore = new TextAndScore();
                textAndScore.score = Double.parseDouble(response);
                textAndScore.text = reader.readLine();

                if (!MESSAGES.DONE.toString().equals(reader.readLine())) {
                    logger.error("Error 2 in communication channel with {}.", server);
                    throw new IOException("Error receiving transcription.");
                }

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
                    e.printStackTrace();
                }
            }
        }

    }

    private static void sleepBeforeRetry(long lastRequestTime) throws InterruptedException {
        long sleep = RETRY_INTERVAL_MILLIS - (System.currentTimeMillis() - lastRequestTime);
        if (sleep > 0) {
            Thread.sleep(sleep);
        }
    }

}
