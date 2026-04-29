package dev.proofly.getmnemo;

import com.sun.net.httpserver.HttpServer;
import dev.proofly.getmnemo.model.AddMemoryInput;
import dev.proofly.getmnemo.model.Memory;
import dev.proofly.getmnemo.model.SearchInput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MnemoClientTest {

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> capturedAuth = new AtomicReference<>();
    private final AtomicReference<String> capturedWorkspace = new AtomicReference<>();
    private final AtomicReference<String> capturedBody = new AtomicReference<>();
    private final AtomicReference<String> capturedPath = new AtomicReference<>();

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/memories", exchange -> {
            capturedPath.set(exchange.getRequestURI().getPath());
            capturedAuth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            capturedWorkspace.set(exchange.getRequestHeaders().getFirst("x-workspace-id"));
            capturedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"id\":\"mem_1\",\"content\":\"hello\",\"createdAt\":\"2026-01-01T00:00:00Z\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(201, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/v1/search", exchange -> {
            byte[] body = "{\"message\":\"bad key\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(401, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void addMemorySendsAuthAndWorkspaceHeaders() throws Exception {
        MnemoClient client = MnemoClient.builder()
                .apiKey("key")
                .workspaceId("ws")
                .baseUrl(baseUrl)
                .build();

        Memory mem = client.memories().add(new AddMemoryInput("hello", null, null));

        assertEquals("mem_1", mem.id());
        assertEquals("/v1/memories", capturedPath.get());
        assertEquals("Bearer key", capturedAuth.get());
        assertEquals("ws", capturedWorkspace.get());
        assertTrue(capturedBody.get().contains("\"content\":\"hello\""));
    }

    @Test
    void searchRaisesApiExceptionOn401() {
        MnemoClient client = MnemoClient.builder()
                .apiKey("x")
                .workspaceId("ws")
                .baseUrl(baseUrl)
                .build();

        ApiException ex = assertThrows(ApiException.class,
                () -> client.search(new SearchInput("hi", null, null)));
        assertEquals(401, ex.status());
        assertTrue(ex.getMessage().contains("bad key"));
    }
}
