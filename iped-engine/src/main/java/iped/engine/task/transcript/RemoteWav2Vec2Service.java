package iped.engine.task.transcript;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.engine.config.AudioTranscriptConfig;
import iped.engine.config.Configuration;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.LocalConfig;
import iped.engine.task.transcript.AbstractTranscriptTask.TextAndScore;
import iped.utils.IOUtil;

public class RemoteWav2Vec2Service {
    
    static enum MESSAGES {
        ACCEPTED,
        AUDIO_SIZE,
        BUSY,
        DISCOVER,
        DONE,
        ERROR,
        REGISTER,
        WARN
    }

    private static final int MAX_CON_QUEUE = 5000;

    private static ExecutorService executor = Executors.newCachedThreadPool();

    private static Logger logger;

    private static void printHelpAndExit() {
        System.out.println(
                "Params: IP:Port [LocalPort]\n"
                + "IP:Port    IP and port of the naming node.\n"
                + "LocalPort  [optional] local port to listen for connections.\n"
                + "           If not provided, a random port will be used.");
        System.exit(1);
    }

    public static void main(String[] args) throws Exception {

        if (args.length == 0 || args.length > 2 || !args[0].contains(":")) {
            printHelpAndExit();
        }

        String[] discoveryAddr = args[0].split(":");
        String discoveryIp = discoveryAddr[0];
        int discoveryPort = 0;
        int localPort = 0;
        try {
            discoveryPort = Integer.parseInt(discoveryAddr[1]);
            if (args.length == 2) {
                localPort = Integer.parseInt(args[1]);
            }
        } catch (NumberFormatException e) {
            printHelpAndExit();
        }

        File jar = new File(RemoteWav2Vec2Service.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        File root = jar.getParentFile().getParentFile();

        System.setProperty("org.apache.logging.log4j.level", "INFO");
        logger = LoggerFactory.getLogger(RemoteWav2Vec2Service.class);

        Configuration.getInstance().loadConfigurables(root.getAbsolutePath());
        ConfigurationManager cm = ConfigurationManager.get();
        AudioTranscriptConfig audioConfig = new AudioTranscriptConfig();
        LocalConfig localConfig = new LocalConfig();
        cm.addObject(audioConfig);
        cm.addObject(localConfig);
        cm.loadConfig(audioConfig);
        cm.loadConfig(localConfig);

        Wav2Vec2TranscriptTask task = new Wav2Vec2TranscriptTask();
        task.init(cm);

        int numConcurrentTranscriptions = Wav2Vec2TranscriptTask.getNumConcurrentTranscriptions();

        try (ServerSocket server = new ServerSocket(localPort, MAX_CON_QUEUE)) {

            server.setSoTimeout(0);
            // server.setReceiveBufferSize((1 << 16) - 1);
            // server.setPerformancePreferences(0, 1, 2);

            localPort = server.getLocalPort();

            registerThis(discoveryIp, discoveryPort, localPort);

            logger.info("Transcription server listening on port: " + localPort);
            logger.info("Ready to work!");

            keepRegisteringThis(discoveryIp, discoveryPort, localPort);

            waitRequests(server, task, numConcurrentTranscriptions, discoveryIp);

        }

    }

    private static void registerThis(String discoveryIp, int discoveryPort, int localPort) throws Exception {
        try (Socket client = new Socket(discoveryIp, discoveryPort);
                InputStream is = client.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                PrintWriter writer = new PrintWriter(
                        new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), true)) {

            writer.println(MESSAGES.REGISTER);
            writer.println(localPort);

            if (!MESSAGES.DONE.toString().equals(reader.readLine())) {
                throw new Exception("Registration failed!");
            }
        }
    }

    private static void waitRequests(ServerSocket server, Wav2Vec2TranscriptTask task, int numConcurrentTranscriptions, String discoveryIp) {
        AtomicInteger jobs = new AtomicInteger();
        while (true) {
            try {
                Socket client = server.accept();
                if (jobs.incrementAndGet() > numConcurrentTranscriptions) {
                    jobs.decrementAndGet();
                    client.close();
                    continue;
                }
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        Path tmpFile = null;
                        File wavFile = null;
                        PrintWriter writer = null;
                        try (BufferedInputStream bis = new BufferedInputStream(client.getInputStream())) {

                            writer = new PrintWriter(
                                    new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), true);
                            writer.println(MESSAGES.ACCEPTED);

                            String clientName = "Client " + client.getInetAddress().getHostAddress() + ":" + client.getPort();
                            logger.info("Accepted connection from " + clientName);

                            String prefix = clientName + " - ";

                            byte[] bytes = bis.readNBytes(MESSAGES.AUDIO_SIZE.toString().length());
                            String cmd = new String(bytes);
                            if (!MESSAGES.AUDIO_SIZE.toString().equals(cmd)) {
                                String errorMsg = "Size msg not received!";
                                writer.println(MESSAGES.ERROR);
                                writer.println(errorMsg);
                                throw new IOException(prefix + errorMsg);
                            }

                            DataInputStream dis = new DataInputStream(bis);
                            int size = dis.readInt();

                            tmpFile = Files.createTempFile("audio", ".tmp");
                            try (OutputStream os = Files.newOutputStream(tmpFile)) {
                                byte[] buf = new byte[8192];
                                int i = 0, read = 0;
                                while (read < size && (i = bis.read(buf)) >= 0) {
                                    os.write(buf, 0, i);
                                    read += i;
                                }
                            }

                            if (tmpFile.toFile().length() != size) {
                                String errorMsg = "Received less audio bytes than expected";
                                writer.println(MESSAGES.ERROR);
                                writer.println(errorMsg);
                                throw new IOException(prefix + errorMsg);
                            } else {
                                logger.info(prefix + "Received " + size + " audio bytes to transcribe.");
                            }

                            String suffix = ".";
                            try {
                                wavFile = task.getWavFile(tmpFile.toFile(), tmpFile.toString());
                            } catch (Exception e) {
                                suffix = ": " + e.toString().replace('\n', ' ').replace('\r', ' ');
                            }
                            if (wavFile == null) {
                                String errorMsg = "Failed to convert audio to wav" + suffix;
                                throw new IOException(errorMsg);
                            } else {
                                logger.info(prefix + "Audio converted to wav.");
                            }

                            TextAndScore result = task.transcribeAudio(wavFile);

                            logger.info(prefix + "Transcritpion done.");

                            writer.println(Double.toString(result.score));
                            writer.println(result.text);
                            writer.println(MESSAGES.DONE);

                            logger.info(prefix + "Transcritpion sent.");

                        } catch (Exception e) {
                            String errorMsg = "Exception while transcribing";
                            logger.warn(errorMsg, e);
                            if (writer != null) {
                                writer.println(MESSAGES.WARN);
                                writer.println(errorMsg + ": " + e.toString());
                            }
                        } finally {
                            jobs.decrementAndGet();
                            IOUtil.closeQuietly(writer);
                            IOUtil.closeQuietly(client);
                            if (tmpFile != null) {
                                tmpFile.toFile().delete();
                            }
                            if (wavFile != null) {
                                wavFile.delete();
                            }
                        }
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void keepRegisteringThis(String ip, int port, int localPort) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(1000);
                        registerThis(ip, port, localPort);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
            }
        });
    }

}
