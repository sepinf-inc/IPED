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
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import iped.engine.task.transcript.RemoteWav2Vec2Service.MESSAGES;

public class RemoteWav2Vec2Discovery {

    private static final int PING_TIMEOUT = 10;
    private static Map<String, Long> servers = new ConcurrentHashMap<>();
    private static int port;

    public static void main(String[] args) {

        port = Integer.parseInt(args[0]);

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
                        register(reader, writer);
                    }
                    if (MESSAGES.DISCOVER.toString().equals(cmd)) {
                        discover(writer);
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private static void register(BufferedReader reader, PrintWriter writer) throws IOException {
        String address = reader.readLine();
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

    private static void monitorServers() {
        new Thread() {
            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
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
