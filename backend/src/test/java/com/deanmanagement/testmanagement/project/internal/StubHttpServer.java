package com.deanmanagement.testmanagement.project.internal;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A local HTTP server for adapter tests that answers queued responses in order and records every
 * request, for providers that need several calls per operation (PRD-026). When the queue runs dry
 * it keeps answering with the last response.
 */
public final class StubHttpServer implements AutoCloseable {

    public record Response(int status, String contentType, String body) {
        public static Response json(String body) {
            return new Response(200, "application/json", body);
        }

        public static Response json(int status, String body) {
            return new Response(status, "application/json", body);
        }
    }

    public record Request(String method, String pathAndQuery, String authorization, String contentType, String body) {
    }

    private final HttpServer server;
    private final Deque<Response> responses = new ArrayDeque<>();
    private final List<Request> requests = new ArrayList<>();
    private Response last = Response.json("{}");

    public StubHttpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            synchronized (this) {
                requests.add(new Request(exchange.getRequestMethod(),
                        exchange.getRequestURI().getRawPath() + (query == null ? "" : "?" + query),
                        exchange.getRequestHeaders().getFirst("Authorization"),
                        exchange.getRequestHeaders().getFirst("Content-Type"),
                        new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
                if (!responses.isEmpty()) {
                    last = responses.poll();
                }
            }
            byte[] body = last.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", last.contentType());
            exchange.sendResponseHeaders(last.status(), body.length == 0 ? -1 : body.length);
            if (body.length > 0) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public synchronized StubHttpServer respond(Response... queued) {
        responses.addAll(List.of(queued));
        return this;
    }

    public synchronized List<Request> requests() {
        return List.copyOf(requests);
    }

    public Request lastRequest() {
        List<Request> all = requests();
        return all.get(all.size() - 1);
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
