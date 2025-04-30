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
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import iped.engine.config.AudioTranscriptConfig;
import iped.engine.config.Configuration;
import iped.engine.config.ConfigurationManager;
import iped.engine.config.LocalConfig;
import iped.engine.task.transcript.AbstractTranscriptTask.TextAndScore;
import iped.io.URLUtil;
import iped.utils.IOUtil;

public class RemoteTranscriptionService {
    // 30 minutos
    private static final int MAX_WAV_TIME = 30 * 60;
    private static final int MAX_WAV_SIZE = 16000 * 2 * MAX_WAV_TIME;

    static enum MESSAGES {
        ACCEPTED, AUDIO_SIZE, BUSY, DISCOVER, DONE, ERROR, REGISTER, STATS, WARN, VERSION_1_1, VERSION_1_2, VERSION_1_0, PING
    }

    static class TranscribeRequest {
        File wavAudio;
        TextAndScore result = null;
        Exception error = null;

        public TranscribeRequest(File wavAudio) {
            this.wavAudio = wavAudio;
        }
    }

    static class OpenConnectons {
        Socket conn;
        BufferedInputStream bis;
        PrintWriter writer;
        Thread t;
        File wavAudio;
        TextAndScore result = null;

        public OpenConnectons(Socket conn, BufferedInputStream bis, PrintWriter writer, Thread t) {
            this.conn = conn;
            this.bis = bis;
            this.writer = writer;
            this.t = t;
        }

        public void sendBeacon() {
            writer.println(MESSAGES.PING.toString());
            writer.flush();
        }
    }

    /**
     * Max number of connections to receive WAVs simultaneously. Also used as
     * backlog value: connection queue waiting for acceptance.
     */
    private static final int MAX_CONNECTIONS = 128;

    // This timeout should not be too high, otherwise clients with connection issues
    // would waste server time waiting for them while good clients are waiting.
    private static final int CLIENT_TIMEOUT_MILLIS = 10000;

    private static ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * Controls max number of simultaneous transcriptions
     */
    private static Semaphore transcriptSemaphore;

    /**
     * Control number of simultaneous audio conversions to WAV.
     */
    private static Semaphore wavConvSemaphore;

    private static int BATCH_SIZE = 1;

    private static final AtomicLong audiosTranscripted = new AtomicLong();
    private static final AtomicLong audiosDuration = new AtomicLong();
    private static final AtomicLong conversionTime = new AtomicLong();
    private static final AtomicLong transcriptionTime = new AtomicLong();
    private static final AtomicLong requestsReceived = new AtomicLong();
    private static final AtomicLong requestsAccepted = new AtomicLong();
    private static final List<OpenConnectons> beaconQueue = new LinkedList<>();
    private static final Deque<TranscribeRequest> toTranscribe = new LinkedList<>();

    private static Logger logger;

    private static void printHelpAndExit() {
        System.out.println("Params: IP:Port [LocalPort]\n" + "IP:Port    IP and port of the naming node.\n" + "LocalPort  [optional] local port to listen for connections.\n" + "           If not provided, a random port will be used.");
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

        File jar = new File(URLUtil.getURL(RemoteTranscriptionService.class).toURI());
        File root = jar.getParentFile().getParentFile();

        System.setProperty("org.apache.logging.log4j.level", "INFO");
        logger = LoggerFactory.getLogger(RemoteTranscriptionService.class);

        Configuration.getInstance().loadConfigurables(root.getAbsolutePath());
        ConfigurationManager cm = ConfigurationManager.get();
        AudioTranscriptConfig audioConfig = new AudioTranscriptConfig();
        LocalConfig localConfig = new LocalConfig();
        cm.addObject(audioConfig);
        cm.addObject(localConfig);
        cm.loadConfig(localConfig);
        cm.loadConfig(audioConfig);

        AbstractTranscriptTask task = (AbstractTranscriptTask) Class.forName(audioConfig.getClassName()).getDeclaredConstructor().newInstance();
        audioConfig.setEnabled(true);
        task.init(cm);
        BATCH_SIZE = audioConfig.getBatchSize();
        int numConcurrentTranscriptions = Wav2Vec2TranscriptTask.getNumConcurrentTranscriptions();
        int numLogicalCores = Runtime.getRuntime().availableProcessors();

        // We already use a BlockingDeque to get an available transcription process,
        // this Semaphore wouldn't be needed, but it guarantees a fairness policy.
        transcriptSemaphore = new Semaphore(numConcurrentTranscriptions, true);

        wavConvSemaphore = new Semaphore(numLogicalCores, true);

        try (ServerSocket server = new ServerSocket(localPort, MAX_CONNECTIONS)) {

            server.setSoTimeout(0);
            // server.setReceiveBufferSize((1 << 16) - 1);
            // server.setPerformancePreferences(0, 1, 2);

            localPort = server.getLocalPort();

            registerThis(discoveryIp, discoveryPort, localPort, numConcurrentTranscriptions, numLogicalCores);

            logger.info("Transcription server listening on port: " + localPort);
            logger.info("Ready to work!");

            startSendStatsThread(discoveryIp, discoveryPort, localPort, numConcurrentTranscriptions, numLogicalCores);

            startBeaconThread();
            for (int i = 0; i < numConcurrentTranscriptions; i++) {
                startTrancribeThreads(task);
            }

            waitRequests(server, task, discoveryIp);

        }

    }

    private static void startBeaconThread() {

        executor.execute(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        if (executor.isShutdown()) {
                            break;
                        }
                        Thread.sleep(60000);
                        logger.info("Send beacons to {} clients", beaconQueue.size());
                        synchronized (beaconQueue) {
                            for (var cliente : beaconQueue) {
                                cliente.sendBeacon();
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
            }
        });
    }

    private static void transcribeAudios(AbstractTranscriptTask task) throws Exception {
        ArrayList<TranscribeRequest> transcribeRequests = new ArrayList<>();
        ArrayList<File> files = new ArrayList<File>();

        if (executor.isShutdown()) {
            throw new Exception("Shutting down service instance...");
        }

        synchronized (toTranscribe) {
            if (toTranscribe.size() == 0)
                return;
            while (toTranscribe.size() > 0 && transcribeRequests.size() < BATCH_SIZE) {
                TranscribeRequest req = toTranscribe.poll();
                transcribeRequests.add(req);
                files.add(req.wavAudio);
            }
        }
        logger.info("inicio da transcricao de " + files.size() + " audios");

        long t2 = System.currentTimeMillis();

        boolean batchTrancribe = (task instanceof WhisperTranscriptTask);
        if (batchTrancribe) {
            try {
                List<TextAndScore> results = ((WhisperTranscriptTask) task).transcribeAudios(files);
                for (int i = 0; i < results.size(); i++) {
                    transcribeRequests.get(i).result = results.get(i);
                }
            } catch (Exception e) {// case fail, try each audio individually
                batchTrancribe = false;
                logger.error("Error while doing batch transcribe " + e.toString());
            }
        }
        if (!batchTrancribe) {// try each audio individually
            for (int i = 0; i < files.size(); i++) {
                try {
                    transcribeRequests.get(i).result = task.transcribeAudio(files.get(i));
                } catch (Exception e2) {
                    transcribeRequests.get(i).result = null;
                    transcribeRequests.get(i).error = e2;
                    logger.error("Error while transcribing");
                }
            }
        }

        for (int i = 0; i < transcribeRequests.size(); i++) {
            synchronized (transcribeRequests.get(i)) {
                transcribeRequests.get(i).notifyAll();
            }
        }

        long t3 = System.currentTimeMillis();
        transcriptionTime.addAndGet(t3 - t2);
    }

    private static void registerThis(String discoveryIp, int discoveryPort, int localPort, int concurrentJobs, int concurrentWavConvs) throws Exception {
        try (Socket client = new Socket(discoveryIp, discoveryPort);
                InputStream is = client.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), true)) {

            client.setSoTimeout(10000);
            writer.println(MESSAGES.REGISTER);
            writer.println(localPort);
            writer.println(concurrentJobs);
            writer.println(concurrentWavConvs);

            if (!MESSAGES.DONE.toString().equals(reader.readLine())) {
                throw new Exception("Registration failed!");
            }
        }
    }

    private static void sendStats(String discoveryIp, int discoveryPort, int localPort, int concurrentJobs, int concurrentWavConvs) throws Exception {
        try (Socket client = new Socket(discoveryIp, discoveryPort);
                InputStream is = client.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), true)) {

            client.setSoTimeout(10000);
            writer.println(MESSAGES.STATS);
            writer.println(localPort);
            writer.println(concurrentJobs);
            writer.println(concurrentWavConvs);
            writer.println(audiosTranscripted.getAndSet(0));
            writer.println(audiosDuration.getAndSet(0));
            writer.println(conversionTime.getAndSet(0));
            writer.println(transcriptionTime.getAndSet(0));
            writer.println(requestsReceived.getAndSet(0));
            writer.println(requestsAccepted.getAndSet(0));

            if (!MESSAGES.DONE.toString().equals(reader.readLine())) {
                throw new Exception("Sending stats failed!");
            }
        }
    }

    private static void removeFromBeaconQueue(OpenConnectons opc) {
        if (opc != null) {
            synchronized (beaconQueue) {
                beaconQueue.remove(opc);
            }
        }
    }

    private static void waitRequests(ServerSocket server, AbstractTranscriptTask task, String discoveryIp) {
        AtomicInteger jobs = new AtomicInteger();
        while (true) {
            try {
                if (executor.isTerminated()) {
                    System.exit(1);
                }
                if (executor.isShutdown()) {
                    Thread.sleep(1000);
                    continue;
                }
                Socket client = server.accept();
                requestsReceived.incrementAndGet();
                if (jobs.incrementAndGet() > MAX_CONNECTIONS) {
                    jobs.decrementAndGet();
                    client.close();
                    continue;
                }
                executor.execute(new Thread() {
                    @Override
                    public void run() {
                        Path tmpFile = null;
                        File wavFile = null;
                        PrintWriter writer = null;
                        BufferedInputStream bis = null;
                        boolean error = false;
                        OpenConnectons opc = null;
                        String protocol = MESSAGES.VERSION_1_0.toString();
                        ArrayList<TranscribeRequest> reqs = null;

                        try {
                            client.setSoTimeout(CLIENT_TIMEOUT_MILLIS);
                            bis = new BufferedInputStream(client.getInputStream());
                            writer = new PrintWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), true);

                            String clientName = "Client " + client.getInetAddress().getHostAddress() + ":" + client.getPort();
                            String prefix = clientName + " - ";
                            writer.println(MESSAGES.ACCEPTED);

                            bis.mark(5);
                            if (bis.read() == -1) {
                                logger.info(prefix + "Possible Kubernetes live test");
                                requestsReceived.decrementAndGet();
                                return;
                            }
                            bis.reset();

                            requestsAccepted.incrementAndGet();

                            logger.info(prefix + "Accepted connection.");

                            byte[] bytes = bis.readNBytes(MESSAGES.VERSION_1_2.toString().length());
                            protocol = new String(bytes);
                            synchronized (beaconQueue) {
                                opc = new OpenConnectons(client, bis, writer, this);
                                beaconQueue.add(opc);
                            }

                            logger.info("Protocol Version {}", protocol);
                            if (protocol.compareTo(MESSAGES.VERSION_1_2.toString()) < 0) {
                                throw new Exception("Procol version " + protocol + " not supported");
                            }

                            // read the audio_size message
                            bytes = bis.readNBytes(MESSAGES.AUDIO_SIZE.toString().length());
                            String cmd = new String(bytes);

                            if (!MESSAGES.AUDIO_SIZE.toString().equals(cmd)) {
                                error = true;
                                throw new IOException("Size msg not received!");
                            }

                            DataInputStream dis = new DataInputStream(bis);
                            long size;
                            size = dis.readLong();

                            if (size < 0) {
                                error = true;
                                try {
                                    OutputStream o = OutputStream.nullOutputStream();
                                    IOUtil.copyInputToOutputStream(dis, o);
                                    o.close();

                                } catch (IOException e) {
                                }
                                throw new Exception("Invalid file size: " + size);

                            }

                            logger.info(prefix + "Receiving " + new DecimalFormat().format(size) + " bytes...");

                            tmpFile = Files.createTempFile("audio", ".tmp");
                            try (OutputStream os = Files.newOutputStream(tmpFile)) {
                                byte[] buf = new byte[8192];
                                int i = 0;
                                long read = 0;
                                while (read < size && (i = bis.read(buf)) >= 0) {
                                    os.write(buf, 0, i);
                                    read += i;
                                    if (executor.isShutdown()) {
                                        error = true;
                                        throw new Exception("Shutting down service instance...");
                                    }
                                }
                            }

                            if (tmpFile.toFile().length() != size) {
                                error = true;
                                throw new IOException("Received less audio bytes than expected");
                            } else {
                                logger.info(prefix + "Received " + size + " audio bytes to transcribe.");
                            }

                            // Now we are converting to WAV on server side again, see
                            // https://github.com/sepinf-inc/IPED/issues/1561
                            long t0, t1;
                            try {
                                wavConvSemaphore.acquire();
                                if (executor.isShutdown()) {
                                    error = true;
                                    throw new Exception("Shutting down service instance...");
                                }
                                t0 = System.currentTimeMillis();
                                wavFile = task.getWavFile(tmpFile.toFile(), tmpFile.toString());
                                t1 = System.currentTimeMillis();
                            } finally {
                                wavConvSemaphore.release();
                            }

                            if (wavFile == null) {
                                throw new IOException("Failed to convert audio to wav");
                            } else {
                                logger.info(prefix + "Audio converted to wav.");
                            }
                            long durationMillis = 1000 * wavFile.length() / (16000 * 2);

                            TextAndScore result = new TextAndScore();
                            result.text = "";
                            result.score = 0;
                            try {
                                reqs = new ArrayList<TranscribeRequest>();
                                TranscribeRequest last = null;
                                if (wavFile.length() <= MAX_WAV_SIZE) {
                                    TranscribeRequest req = new TranscribeRequest(wavFile);
                                    reqs.add(req);

                                } else {

                                    for (File wavPart : AbstractTranscriptTask.getAudioSplits(wavFile, wavFile.getPath(), MAX_WAV_TIME)) {
                                        TranscribeRequest req = new TranscribeRequest(wavPart);
                                        reqs.add(req);
                                    }
                                    logger.info(prefix + "Audio breaked into {} parts", reqs.size());
                                    wavFile.delete();

                                }
                                wavFile = null;

                                // dispatch all parts to be executed
                                for (TranscribeRequest req : reqs) {
                                    synchronized (toTranscribe) {
                                        toTranscribe.add(req);
                                    }
                                    last = req;
                                }

                                // wait until the last wav part is transcribed
                                synchronized (last) {
                                    last.wait();
                                }

                                for (TranscribeRequest req : reqs) {
                                    TextAndScore partResult = req.result;
                                    if (partResult == null || req.error != null) {
                                        error = false;
                                        throw req.error != null ? req.error : new Exception("Error processing the audio");
                                    }

                                    if (result.score > 0)
                                        result.text += " ";
                                    result.text += partResult.text;
                                    result.score += partResult.score;
                                }
                                result.score /= reqs.size();

                            } catch (ProcessCrashedException e) {
                                // retry audio
                                error = true;
                                throw e;
                            } catch (StartupException e) {
                                error = true;
                                // graceful shutdown to clean resources like temp files
                                executor.shutdown();
                                server.close();
                                throw e;
                            }

                            audiosTranscripted.incrementAndGet();
                            audiosDuration.addAndGet(durationMillis);
                            conversionTime.addAndGet(t1 - t0);

                            logger.info(prefix + "Transcritpion done.");

                            // removes from the beacon queue to prevent beacons in the middle of the
                            // transcription
                            removeFromBeaconQueue(opc);

                            writer.println(Double.toString(result.score));
                            writer.println(result.text);
                            writer.println(MESSAGES.DONE);
                            writer.flush();
                            logger.info(prefix + "Transcritpion sent.");

                        } catch (Exception e) {
                            String errorMsg = "Exception while transcribing";
                            logger.warn(errorMsg, e);
                            if (writer != null) {
                                writer.println(error ? MESSAGES.ERROR : MESSAGES.WARN);
                                writer.println(errorMsg + ": " + e.toString().replace('\n', ' ').replace('\r', ' '));
                                writer.flush();
                            }
                        } finally {
                            jobs.decrementAndGet();
                            IOUtil.closeQuietly(bis);
                            IOUtil.closeQuietly(writer);
                            IOUtil.closeQuietly(client);
                            if (tmpFile != null) {
                                tmpFile.toFile().delete();
                            }
                            if (wavFile != null) {
                                wavFile.delete();
                            }
                            if (reqs != null) {
                                for (TranscribeRequest req : reqs) {
                                    if (req.wavAudio != null && req.wavAudio != wavFile) {
                                        req.wavAudio.delete();
                                    }
                                }
                            }
                            removeFromBeaconQueue(opc);
                        }
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void startSendStatsThread(String ip, int port, int localPort, int concurrentJobs, int concurrentWavConvs) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        if (executor.isShutdown()) {
                            break;
                        }
                        Thread.sleep(1000);
                        sendStats(ip, port, localPort, concurrentJobs, concurrentWavConvs);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
            }
        });
    }

    private static void startTrancribeThreads(AbstractTranscriptTask task) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    Boolean empty = true;
                    synchronized (toTranscribe) {
                        empty = toTranscribe.isEmpty();
                    }
                    if (empty) {
                        try {
                            Thread.sleep(100);

                        } catch (Exception e) {
                            // TODO: handle exception
                        }
                        continue;
                    }
                    try {
                        transcriptSemaphore.acquire();
                        transcribeAudios(task);

                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        transcriptSemaphore.release();
                    }

                }
            }
        });
    }

}
