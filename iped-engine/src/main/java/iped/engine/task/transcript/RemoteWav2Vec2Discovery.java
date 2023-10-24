package iped.engine.task.transcript;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import iped.engine.task.transcript.RemoteWav2Vec2Service.MESSAGES;

public class RemoteWav2Vec2Discovery {

    private static final File statsFile = new File(System.getProperty("user.home"), "transcription.stats");

    private static final int PING_TIMEOUT = 10;
    private static Map<String, Long> servers = new ConcurrentHashMap<>();
    private static Map<String, Integer> concurrentJobs = new ConcurrentHashMap<>();
    private static Map<String, Integer> concurrentWavConvs = new ConcurrentHashMap<>();
    private static int port;
    private static long startTime = 0;

    private static final AtomicLong audiosTranscripted = new AtomicLong();
    private static final AtomicLong audiosDuration = new AtomicLong();
    private static final AtomicLong conversionTimeCpu = new AtomicLong();
    private static final AtomicLong conversionTimeReal = new AtomicLong();
    private static final AtomicLong transcriptionTimeCpu = new AtomicLong();
    private static final AtomicLong transcriptionTimeReal = new AtomicLong();
    private static final AtomicLong requestsReceived = new AtomicLong();
    private static final AtomicLong requestsAccepted = new AtomicLong();

    private static void printHelpAndExit() {
        System.out.println("You must pass a free PORT number as parameter!");
        System.exit(1);
    }

    public static void main(String[] args) {

        if (args.length != 1) {
            printHelpAndExit();
        }

        try {
            port = Integer.parseInt(args[0]);

        } catch (NumberFormatException e) {
            printHelpAndExit();
        }

        try (ServerSocket server = new ServerSocket(port)) {

            server.setSoTimeout(0);

            System.out.println(new Date() + " Service discovery running on " + server.getInetAddress().getHostAddress() + ":" + port);
            startTime = System.currentTimeMillis();

            loadStats();

            monitorServers();

            while (true) {
                try (Socket client = server.accept();
                        InputStream is = client.getInputStream();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                        PrintWriter writer = new PrintWriter(new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), true)) {
                    client.setSoTimeout(10000);
                    String cmd = reader.readLine();
                    if (MESSAGES.REGISTER.toString().equals(cmd)) {
                        register(client, reader, writer);
                    }
                    if (MESSAGES.DISCOVER.toString().equals(cmd)) {
                        discover(writer);
                    }
                    if (MESSAGES.STATS.toString().equals(cmd)) {
                        getStats(client, reader, writer);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static void register(Socket client, BufferedReader reader, PrintWriter writer) throws IOException {
        String ip = client.getInetAddress().getHostAddress();
        System.out.println(new Date() + " Receiving registration from: " + ip);
        String port = reader.readLine();
        String address = ip + ":" + port;
        String nodeJobs = reader.readLine();
        String nodeWavConvs = reader.readLine();
        writer.println(MESSAGES.DONE);
        if (servers.put(address, System.currentTimeMillis()) == null) {
            System.out.println(new Date() + " Server registered: " + address);
            concurrentJobs.put(address, Integer.valueOf(nodeJobs));
            concurrentWavConvs.put(address, Integer.valueOf(nodeWavConvs));
        }
    }

    private static void discover(PrintWriter writer) throws IOException {
        String env_servers = System.getenv("IPED_TRANSCRIPTION_SERVERS");
        if (!servers.isEmpty() && env_servers != null && !env_servers.trim().isEmpty()) {
            String servers[] = env_servers.trim().split(",");
            writer.println(Integer.toString(servers.length));
            for (String server : servers) {
                writer.println(server.trim());
            }
        } else {
            writer.println(Integer.toString(servers.size()));
            for (String server : servers.keySet()) {
                writer.println(server);
            }
        }

    }

    private static void getStats(Socket client, BufferedReader reader, PrintWriter writer) throws IOException {
        String ip = client.getInetAddress().getHostAddress();
        String port = reader.readLine();
        String address = ip + ":" + port;
        String nodeJobs = reader.readLine();
        String nodeWavConvs = reader.readLine();

        audiosTranscripted.addAndGet(Long.parseLong(reader.readLine()));
        audiosDuration.addAndGet(Long.parseLong(reader.readLine()));
        long convTime = Long.parseLong(reader.readLine());
        conversionTimeCpu.addAndGet(convTime);
        long transcriptTime = Long.parseLong(reader.readLine());
        transcriptionTimeCpu.addAndGet(transcriptTime);
        requestsReceived.addAndGet(Long.parseLong(reader.readLine()));
        requestsAccepted.addAndGet(Long.parseLong(reader.readLine()));

        writer.println(MESSAGES.DONE);

        if (servers.put(address, System.currentTimeMillis()) == null) {
            System.out.println(new Date() + " Server registered: " + address);
            concurrentJobs.put(address, Integer.valueOf(nodeJobs));
            concurrentWavConvs.put(address, Integer.valueOf(nodeWavConvs));
        }

        int totalJobs = concurrentJobs.values().stream().reduce(0, Integer::sum);
        int totalWavConvs = concurrentWavConvs.values().stream().reduce(0, Integer::sum);

        transcriptionTimeReal.addAndGet(transcriptTime / totalJobs);
        conversionTimeReal.addAndGet(convTime / totalWavConvs);
    }

    private static void loadStats() throws IOException {
        if (!statsFile.exists()) {
            return;
        }
        List<String> lines = Files.readAllLines(statsFile.toPath());
        String value = lines.get(0).split("=")[1];
        audiosTranscripted.set(Long.valueOf(value));
        value = lines.get(1).split("=")[1];
        audiosDuration.set(Long.valueOf(value));
        value = lines.get(2).split("=")[1];
        conversionTimeCpu.set(Long.valueOf(value));
        value = lines.get(3).split("=")[1];
        conversionTimeReal.set(Long.valueOf(value));
        value = lines.get(4).split("=")[1];
        transcriptionTimeCpu.set(Long.valueOf(value));
        value = lines.get(5).split("=")[1];
        transcriptionTimeReal.set(Long.valueOf(value));
        value = lines.get(6).split("=")[1];
        requestsReceived.set(Long.valueOf(value));
        value = lines.get(7).split("=")[1];
        requestsAccepted.set(Long.valueOf(value));
    }

    private static void saveStats() {
        ArrayList<String> lines = new ArrayList<String>();
        lines.add("audiosTranscribed=" + Long.toString(audiosTranscripted.get()));
        lines.add("audiosDuration=" + Long.toString(audiosDuration.get()));
        lines.add("conversionTimeCpu=" + Long.toString(conversionTimeCpu.get()));
        lines.add("conversionTimeReal=" + Long.toString(conversionTimeReal.get()));
        lines.add("transcriptionTimeCpu=" + Long.toString(transcriptionTimeCpu.get()));
        lines.add("transcriptionTimeReal=" + Long.toString(transcriptionTimeReal.get()));
        lines.add("requestsReceived=" + Long.toString(requestsReceived.get()));
        lines.add("requestsAccepted=" + Long.toString(requestsAccepted.get()));
        try {
            Files.write(statsFile.toPath(), lines);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void monitorServers() {
        new Thread() {
            @Override
            public void run() {
                int seconds = 0;
                while (true) {
                    try {
                        Thread.sleep(1000);
                        seconds++;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (seconds == 30) {
                        seconds = 0;
                        DecimalFormat df = new DecimalFormat();
                        int totalJobs = concurrentJobs.values().stream().reduce(0, Integer::sum);
                        int totalWavConvs = concurrentWavConvs.values().stream().reduce(0, Integer::sum);
                        System.out.println("Statistics:");
                        System.out.println("Online Nodes: " + servers.size());
                        System.out.println("Concurrent Transcriptions: " + totalJobs);
                        System.out.println("Concurrent WAV Conversions: " + totalWavConvs);
                        System.out.println("Online Time: " + df.format((System.currentTimeMillis() - startTime) / 1000) + "s");
                        System.out.println("Transcribed Audios: " + df.format(audiosTranscripted));
                        System.out.println("Transcribed Audios Duration: " + df.format(audiosDuration.get() / 1000) + "s");
                        System.out.println("Transcription Time (cpu): " + df.format(transcriptionTimeCpu.get() / 1000) + "s");
                        System.out.println("Transcription Time (real): " + df.format(transcriptionTimeReal.get() / 1000) + "s");
                        System.out.println("Wav Conversion Time (cpu): " + df.format(conversionTimeCpu.get() / 1000) + "s");
                        System.out.println("Wav Conversion Time (real): " + df.format(conversionTimeReal.get() / 1000) + "s");
                        System.out.println("Received Requets: " + df.format(requestsReceived));
                        System.out.println("Accepted Requests: " + df.format(requestsAccepted));
                        System.out.println("-------------------------------------------------------");
                        saveStats();
                    }

                    Iterator<String> it = servers.keySet().iterator();
                    while (it.hasNext()) {
                        String server = it.next();
                        if (System.currentTimeMillis() - servers.get(server) >= PING_TIMEOUT * 1000) {
                            System.out.println(new Date() + " No PING received for the last " + PING_TIMEOUT
                                    + "s, removing server from list: " + server);
                            it.remove();
                            concurrentJobs.remove(server);
                            concurrentWavConvs.remove(server);
                        }
                    }
                }
            }
        }.start();
    }

}
