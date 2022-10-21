package iped.engine.task.transcript;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import iped.engine.task.transcript.RemoteWav2Vec2Service.MESSAGES;

public class RemoteWav2Vec2Discovery {

    private static final int PING_TIMEOUT = 10;
    private static Map<String, Long> servers = new ConcurrentHashMap<>();
    private static int port;

    private static final AtomicLong audiosTranscripted = new AtomicLong();
    private static final AtomicLong conversionTime = new AtomicLong();
    private static final AtomicLong transcriptionTime = new AtomicLong();
    private static final AtomicLong audiosDuration = new AtomicLong();
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

            System.out.println("Service discovery running on " + server.getInetAddress().getHostAddress() + ":" + port);

            monitorServers();

            while (true) {
                try (Socket client = server.accept();
                        InputStream is = client.getInputStream();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                        PrintWriter writer = new PrintWriter(
                                new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8), true)) {
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
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static void register(Socket client, BufferedReader reader, PrintWriter writer) throws IOException {
        String ip = client.getInetAddress().getHostAddress();
        String port = reader.readLine();
        String address = ip + ":" + port;
        writer.println(MESSAGES.DONE);
        if (servers.put(address, System.currentTimeMillis()) == null) {
            System.out.println("Server registered: " + address);
        }
    }

    private static void discover(PrintWriter writer) throws IOException {
        writer.println(Integer.toString(servers.size()));
        for (String server : servers.keySet()) {
            writer.println(server);
        }
    }

    private static void getStats(Socket client, BufferedReader reader, PrintWriter writer) throws IOException {
        String ip = client.getInetAddress().getHostAddress();
        String port = reader.readLine();
        String address = ip + ":" + port;

        audiosTranscripted.addAndGet(Long.parseLong(reader.readLine()));
        audiosDuration.addAndGet(Long.parseLong(reader.readLine()));
        conversionTime.addAndGet(Long.parseLong(reader.readLine()));
        transcriptionTime.addAndGet(Long.parseLong(reader.readLine()));
        requestsReceived.addAndGet(Long.parseLong(reader.readLine()));
        requestsAccepted.addAndGet(Long.parseLong(reader.readLine()));

        writer.println(MESSAGES.DONE);

        if (servers.put(address, System.currentTimeMillis()) == null) {
            System.out.println("Server registered: " + address);
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
                    if (seconds == 10) {
                        seconds = 0;
                        DecimalFormat df = new DecimalFormat();
                        System.out.println("Statistics:");
                        System.out.println("Online Nodes: " + servers.size());
                        System.out.println("Transcribed Audios: " + df.format(audiosTranscripted));
                        System.out.println("Transcribed Audios Duration: " + df.format(audiosDuration.get() / 1000) + "s");
                        System.out.println("Transcription Time: " + df.format(transcriptionTime.get() / 1000) + "s");
                        System.out.println("Wav Conversion Time: " + df.format(conversionTime.get() / 1000) + "s");
                        System.out.println("Received Requets: " + df.format(requestsReceived));
                        System.out.println("Accepted Requests: " + df.format(requestsAccepted));
                        System.out.println("-------------------------------------------------------");
                    }

                    Iterator<String> it = servers.keySet().iterator();
                    while (it.hasNext()) {
                        String server = it.next();
                        if (System.currentTimeMillis() - servers.get(server) >= PING_TIMEOUT * 1000) {
                            System.out.println("No PING received for the last " + PING_TIMEOUT
                                    + "s, removing server from list: " + server);
                            it.remove();
                        }
                    }
                }
            }
        }.start();
    }

}
