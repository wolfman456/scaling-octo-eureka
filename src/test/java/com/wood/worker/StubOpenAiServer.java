package com.wood.worker;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public final class StubOpenAiServer {

    private final HttpServer server;
    private volatile String responseBody = "{}";
    private volatile int statusCode = 200;
    private volatile String lastRequestBody = "";
    private volatile String lastAuthorization = "";

    public StubOpenAiServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            lastRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            lastAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public String baseUrl() {
        return "http://" + server.getAddress().getAddress().getHostAddress() + ":" + port();
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    public void respond(int statusCode, String responseBody) {
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public void respond(String responseBody) {
        respond(200, responseBody);
    }

    public String lastRequestBody() {
        return lastRequestBody;
    }

    public String lastAuthorization() {
        return lastAuthorization;
    }
}