package com.doctorexplorer.clinical.support;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/** core-api de mentira: serve a chave pública do token interno e guarda os eventos de auditoria recebidos. */
public final class StubCore {

    public static final List<String> AUDIT = new CopyOnWriteArrayList<>();
    public static final AtomicInteger AUDIT_STATUS = new AtomicInteger(201);
    private static HttpServer server;

    private StubCore() {}

    public static synchronized String baseUrl() {
        if (server == null) {
            try {
                server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            server.createContext("/api/v1/internal/public-key", ex -> {
                byte[] b = ("{\"alg\":\"Ed25519\",\"spki\":\"" + TestTokens.publicKeyBase64() + "\"}").getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(200, b.length);
                ex.getResponseBody().write(b);
                ex.close();
            });
            server.createContext("/api/v1/internal/audit", ex -> {
                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                String auth = ex.getRequestHeaders().getFirst("Authorization");
                int status = AUDIT_STATUS.get();
                if (status == 201 && auth != null && auth.startsWith("DE-Internal ")) AUDIT.add(body);
                byte[] b = "{}".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(auth == null ? 401 : status, b.length);
                ex.getResponseBody().write(b);
                ex.close();
            });
            server.start();
        }
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }
}
