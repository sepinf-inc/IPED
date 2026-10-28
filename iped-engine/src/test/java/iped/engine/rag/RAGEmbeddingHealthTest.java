package iped.engine.rag;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import iped.engine.config.RAGConfig;

/** The startup health check must validate the embedding model, not only the server. */
public class RAGEmbeddingHealthTest {

    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    private HttpServer server;

    @After
    public void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    public void testAvailableWhenModelReturnsVector() throws Exception {
        RAGService service = service(200, "{\"embedding\":[0.1,0.2,0.3]}");
        try {
            assertTrue(service.isAvailable());
            assertNull(service.getUnavailableReason());
        } finally {
            service.close();
        }
    }

    @Test
    public void testUnavailableWhenModelIsMissing() throws Exception {
        RAGService service = service(404, "{\"error\":\"model 'synthetic' not found\"}");
        try {
            assertFalse(service.isAvailable());
            assertTrue(service.getUnavailableReason(), service.getUnavailableReason().contains("not found"));
        } finally {
            service.close();
        }
    }

    @Test
    public void testUnavailableWhenEndpointIsUnreachable() throws Exception {
        RAGService service = new RAGService(temporary.newFolder(), config("http://127.0.0.1:1/api/embeddings"));
        try {
            assertFalse(service.isAvailable());
            assertTrue(service.getUnavailableReason(), service.getUnavailableReason().contains("unreachable"));
        } finally {
            service.close();
        }
    }

    @Test
    public void testDimensionsAreDetectedFromTheModelWhenNotConfigured() throws Exception {
        RAGService service = service(200, "{\"embedding\":[0.1,0.2,0.3]}");
        try {
            assertEquals(3, service.getConfig().getEmbeddingDimensions());
        } finally {
            service.close();
        }
    }

    @Test
    public void testAnalysisRecoversWhenServiceComesBack() throws Exception {
        AtomicInteger status = new AtomicInteger(503);
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/", exchange -> {
            boolean embedding = exchange.getRequestURI().getPath().endsWith("/api/embeddings");
            respond(exchange, embedding ? status.get() : 200, embedding ? "{\"embedding\":[0.1,0.2,0.3]}" : "{}");
        });
        server.start();
        RAGService service = new RAGService(temporary.newFolder(),
                config("http://127.0.0.1:" + server.getAddress().getPort() + "/api/embeddings"));
        try {
            assertFalse(service.isAvailable());
            status.set(200);
            // within the retry interval the service is not checked again
            assertNull(service.getEmbedding("pergunta sintética"));
            setLastHealthCheck(service, 0);
            assertEquals(3, service.getEmbedding("pergunta sintética").length);
            assertTrue(service.isAvailable());
            assertNull(service.getUnavailableReason());
        } finally {
            service.close();
        }
    }

    private static void setLastHealthCheck(RAGService service, long millis) throws Exception {
        java.lang.reflect.Field field = RAGService.class.getDeclaredField("lastHealthCheckMillis");
        field.setAccessible(true);
        field.setLong(service, millis);
    }

    private RAGService service(int embeddingStatus, String embeddingBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        server.createContext("/", exchange -> {
            boolean embedding = exchange.getRequestURI().getPath().endsWith("/api/embeddings");
            respond(exchange, embedding ? embeddingStatus : 200, embedding ? embeddingBody : "{}");
        });
        server.start();
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/embeddings";
        return new RAGService(temporary.newFolder(), config(endpoint));
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        try (InputStream input = exchange.getRequestBody()) {
            input.readAllBytes();
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static RAGConfig config(String endpoint) {
        return new RAGConfig() {
            @Override
            public boolean isEnabled() {
                return true;
            }

            @Override
            public String getEmbeddingEndpoint() {
                return endpoint;
            }

            @Override
            public String getEmbeddingModel() {
                return "synthetic";
            }
        };
    }
}
