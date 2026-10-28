package iped.engine.rag;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public class LocalLLMProviderStreamTest {

    @Test
    public void testNormalCompletionPreservesChunksAndRequest() throws Exception {
        try (Server server = new Server(Mode.NORMAL)) {
            List<String> tokens = new ArrayList<>();
            String answer = server.provider(Duration.ofSeconds(5)).generateAnswerStream("synthetic question",
                    "synthetic context", Collections.emptyList(), tokens::add);
            assertEquals("First\nSecond", answer);
            assertEquals(java.util.Arrays.asList("First\n", "Second"), tokens);
            assertTrue(server.headers.startsWith("POST /api/chat HTTP/1.1"));
            com.google.gson.JsonObject request = com.google.gson.JsonParser.parseString(server.requestBody).getAsJsonObject();
            assertEquals("synthetic-model", request.get("model").getAsString());
            assertTrue(request.get("stream").getAsBoolean());
            assertEquals(0.0, request.getAsJsonObject("options").get("temperature").getAsDouble(), 0.0);
            int window = request.getAsJsonObject("options").get("num_ctx").getAsInt();
            assertEquals(LocalLLMProvider.generationTokenReserve(window),
                    request.getAsJsonObject("options").get("num_predict").getAsInt());
            String user = request.getAsJsonArray("messages").get(1).getAsJsonObject().get("content").getAsString();
            LocalLLMProvider provider = server.provider(Duration.ofSeconds(5));
            assertEquals(provider.buildPrompt("synthetic question", "synthetic context", Collections.emptyList())
                    .get(1).content, user);
        }
        awaitNoReaders();
    }

    @Test(timeout = 15000)
    public void testGenerationLimitIsVisibleInAnswerAndStream() throws Exception {
        try (Server server = new Server(Mode.LENGTH)) {
            List<String> tokens = new ArrayList<>();
            String answer = server.provider(Duration.ofSeconds(5)).generateAnswerStream("q", "c",
                    Collections.emptyList(), tokens::add);
            assertEquals("Cut" + LocalLLMProvider.truncationNotice(), answer);
            assertEquals(java.util.Arrays.asList("Cut", LocalLLMProvider.truncationNotice()), tokens);
            assertFalse(LocalLLMProvider.truncationNotice().trim().isEmpty());
        }
        awaitNoReaders();
    }

    @Test(timeout = 15000)
    public void testSilentBodyTimesOutAndClosesConnection() throws Exception {
        try (Server server = new Server(Mode.SILENT)) {
            expectTimeout(server);
            assertTrue(server.peerClosed.await(3, TimeUnit.SECONDS));
        }
        awaitNoReaders();
    }

    @Test(timeout = 15000)
    public void testPartialAnswerDoesNotBecomeSuccessfulOnTimeout() throws Exception {
        try (Server server = new Server(Mode.PARTIAL)) {
            List<String> tokens = new ArrayList<>();
            try {
                server.provider(Duration.ofMillis(800)).generateAnswerStream("q", "c", Collections.emptyList(), tokens::add);
                fail("Incomplete response must not be returned as a successful answer");
            } catch (HttpTimeoutException expected) {
                assertEquals(Collections.singletonList("Partial"), tokens);
            }
            assertTrue(server.peerClosed.await(3, TimeUnit.SECONDS));
        }
        awaitNoReaders();
    }

    @Test(timeout = 15000)
    public void testInterruptReleasesAlreadyStreamingResponse() throws Exception {
        try (Server server = new Server(Mode.PARTIAL)) {
            CountDownLatch tokenReceived = new CountDownLatch(1);
            CountDownLatch ended = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            AtomicReference<String> answer = new AtomicReference<>();
            Thread caller = new Thread(() -> {
                try {
                    answer.set(server.provider(Duration.ofSeconds(10)).generateAnswerStream("q", "c",
                            Collections.emptyList(), token -> tokenReceived.countDown()));
                } catch (Throwable e) {
                    failure.set(e);
                } finally {
                    ended.countDown();
                }
            }, "synthetic-LLM-caller");
            caller.setDaemon(true);
            caller.start();
            try {
                assertTrue(tokenReceived.await(5, TimeUnit.SECONDS));
                caller.interrupt();
                assertTrue(ended.await(3, TimeUnit.SECONDS));
                assertTrue(failure.get() instanceof InterruptedException);
                assertEquals(null, answer.get());
                assertTrue(server.peerClosed.await(3, TimeUnit.SECONDS));
            } finally {
                caller.interrupt();
                caller.join(3000);
            }
        }
        awaitNoReaders();
    }

    @Test(timeout = 15000)
    public void testStreamingProgressDoesNotExtendTotalDeadline() throws Exception {
        try (Server server = new Server(Mode.CONTINUOUS)) {
            CountDownLatch tokenReceived = new CountDownLatch(1);
            try {
                server.provider(Duration.ofMillis(800)).generateAnswerStream("q", "c", Collections.emptyList(),
                        token -> tokenReceived.countDown());
                fail("Ongoing chunks must not extend the total deadline");
            } catch (HttpTimeoutException expected) {
                assertEquals(0, tokenReceived.getCount());
            }
        }
        awaitNoReaders();
    }

    @Test(timeout = 15000)
    public void testErrorStatusAndErrorBodyTimeout() throws Exception {
        try (Server server = new Server(Mode.ERROR)) {
            try {
                server.provider(Duration.ofSeconds(5)).generateAnswer("q", "c");
                fail("Expected HTTP error");
            } catch (IOException expected) {
                assertTrue(expected.getMessage().contains("503"));
                assertTrue(expected.getMessage().contains("synthetic unavailable"));
            }
        }
        try (Server server = new Server(Mode.ERROR_SILENT)) {
            expectTimeout(server);
            assertTrue(server.peerClosed.await(3, TimeUnit.SECONDS));
        }
        awaitNoReaders();
    }

    @Test
    public void testConsumerFailureIsPreservedAndClosesReader() throws Exception {
        IllegalStateException failure = new IllegalStateException("Synthetic consumer failure");
        try (Server server = new Server(Mode.PARTIAL)) {
            try {
                server.provider(Duration.ofSeconds(5)).generateAnswerStream("q", "c", Collections.emptyList(),
                        token -> { throw failure; });
                fail("Expected consumer failure");
            } catch (IllegalStateException expected) {
                assertSame(failure, expected);
            }
            assertTrue(server.peerClosed.await(3, TimeUnit.SECONDS));
        }
        awaitNoReaders();
    }

    private static void expectTimeout(Server server) throws Exception {
        try {
            server.provider(Duration.ofMillis(800)).generateAnswer("q", "c");
            fail("Expected full-stream timeout");
        } catch (HttpTimeoutException expected) {
            assertTrue(server.headers != null); // Headers arrived; this is a body-read timeout.
        }
    }

    private static void awaitNoReaders() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        do {
            boolean alive = Thread.getAllStackTraces().keySet().stream()
                    .anyMatch(thread -> thread.isAlive() && "RAG-local-LLM-response".equals(thread.getName()));
            if (!alive) {
                return;
            }
            Thread.sleep(10);
        } while (System.nanoTime() < deadline);
        fail("Local LLM reader thread leaked");
    }

    private enum Mode {
        NORMAL, LENGTH, SILENT, PARTIAL, CONTINUOUS, ERROR, ERROR_SILENT
    }

    private static final class Server implements AutoCloseable {
        final ServerSocket listener;
        final Thread server;
        final CountDownLatch peerClosed = new CountDownLatch(1);
        volatile Socket socket;
        volatile String headers;
        volatile String requestBody;

        Server(Mode mode) throws IOException {
            listener = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
            server = new Thread(() -> serve(mode), "synthetic-LLM-server");
            server.setDaemon(true);
            server.start();
        }

        LocalLLMProvider provider(Duration timeout) {
            return new LocalLLMProvider("http://127.0.0.1:" + listener.getLocalPort() + "/v1", "synthetic-model", timeout);
        }

        private void serve(Mode mode) {
            try (Socket connection = listener.accept()) {
                socket = connection;
                connection.setSoTimeout(10000);
                InputStream input = connection.getInputStream();
                ByteArrayOutputStream header = new ByteArrayOutputStream();
                int c;
                while ((c = input.read()) >= 0) {
                    header.write(c);
                    byte[] bytes = header.toByteArray();
                    int n = bytes.length;
                    if (n >= 4 && bytes[n - 4] == '\r' && bytes[n - 3] == '\n'
                            && bytes[n - 2] == '\r' && bytes[n - 1] == '\n') {
                        break;
                    }
                }
                headers = header.toString(StandardCharsets.US_ASCII.name());
                int length = 0;
                for (String line : headers.split("\r\n")) {
                    if (line.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:")) {
                        length = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
                    }
                }
                requestBody = new String(input.readNBytes(length), StandardCharsets.UTF_8);
                OutputStream output = connection.getOutputStream();
                boolean error = mode == Mode.ERROR || mode == Mode.ERROR_SILENT;
                output.write(((error ? "HTTP/1.1 503 Unavailable" : "HTTP/1.1 200 OK")
                        + "\r\nContent-Type: application/x-ndjson\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n")
                                .getBytes(StandardCharsets.US_ASCII));
                output.flush();
                if (mode == Mode.NORMAL) {
                    chunk(output, "{\"message\":{\"content\":\"First\\n\"}}\n{\"message\":{\"content\":\"Second\"}}\n{\"done\":true}\n");
                } else if (mode == Mode.LENGTH) {
                    chunk(output, "{\"message\":{\"content\":\"Cut\"}}\n"
                            + "{\"message\":{\"content\":\"\"},\"done_reason\":\"length\",\"done\":true}\n");
                } else if (mode == Mode.ERROR) {
                    chunk(output, "synthetic unavailable");
                } else if (mode == Mode.PARTIAL || mode == Mode.CONTINUOUS) {
                    chunk(output, "{\"message\":{\"content\":\"Partial\"}}\n");
                }
                if (mode == Mode.NORMAL || mode == Mode.LENGTH || mode == Mode.ERROR) {
                    output.write("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                    output.flush();
                } else if (mode == Mode.CONTINUOUS) {
                    while (!Thread.currentThread().isInterrupted()) {
                        Thread.sleep(40);
                        chunk(output, "{\"message\":{\"content\":\".\"}}\n");
                    }
                } else {
                    try {
                        while (input.read() >= 0) { }
                    } catch (SocketException expectedClose) {
                        // The client may reset rather than gracefully close the connection.
                    }
                    peerClosed.countDown();
                }
            } catch (Exception e) {
                // Closing a timed-out/cancelled synthetic connection terminates the server.
            }
        }

        private static void chunk(OutputStream output, String text) throws IOException {
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            output.write((Integer.toHexString(bytes.length) + "\r\n").getBytes(StandardCharsets.US_ASCII));
            output.write(bytes);
            output.write("\r\n".getBytes(StandardCharsets.US_ASCII));
            output.flush();
        }

        @Override
        public void close() throws Exception {
            listener.close();
            if (socket != null) {
                socket.close();
            }
            server.interrupt();
            server.join(3000);
        }
    }
}
