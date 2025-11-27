package hara.lang.service;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import hara.lang.base.Ex;
import hara.lang.base.G;
import hara.lang.kernel.Foundation;
import hara.lang.lib.RT;

public class Http {

    private HttpServer server;
    private final Foundation foundation;

    public Http(Foundation foundation) {
        this.foundation = foundation;
    }

    public void start(int port) {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", new InfoHandler());
            server.createContext("/eval", new EvalHandler());
            server.createContext("/session", new SessionHandler());
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            System.out.println("HTTP Server started on port " + port);
        } catch (IOException e) {
            throw Ex.Sneaky(e);
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private class InfoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String response = "Foundation HTTP Gateway Active";
            sendResponse(t, 200, response);
        }
    }

    private class SessionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("GET".equals(t.getRequestMethod())) {
                List<String> sessions = new ArrayList<>(foundation.RTS.keySet());
                String response = G.display(sessions);
                sendResponse(t, 200, response);
            } else if ("POST".equals(t.getRequestMethod())) {
                // CREATE NEW SESSION
                // Expects plain text body: "session_name limit"
                String body = readBody(t);
                String[] parts = body.split(" ");
                String name = parts[0];
                long limit = parts.length > 1 ? Long.parseLong(parts[1]) : 0;

                foundation.RTS.put(name, new RT.Instance(foundation, name, limit));
                sendResponse(t, 200, "Created " + name);
            } else {
                sendResponse(t, 405, "Method Not Allowed");
            }
        }
    }

    private class EvalHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            if ("POST".equals(t.getRequestMethod())) {
                try {
                    // Simple Protocol: Header "X-Session-ID", Body = Code
                    String sessionId = t.getRequestHeaders().getFirst("X-Session-ID");
                    String code = readBody(t);

                    if (sessionId == null) {
                        sendResponse(t, 400, "Missing X-Session-ID header");
                        return;
                    }

                    Object result = Foundation.Fn.runSessionFor(foundation, sessionId,
                        rt -> {
                            try {
                                return rt.eval(rt.readString(code));
                            } catch (Throwable e) {
                                return e;
                            }
                        }
                    );

                    String response = G.display(result);
                    sendResponse(t, 200, response);
                } catch (Exception e) {
                    e.printStackTrace();
                    sendResponse(t, 500, "Error: " + e.getMessage());
                }
            } else {
                sendResponse(t, 405, "Method Not Allowed");
            }
        }
    }

    private String readBody(HttpExchange t) throws IOException {
        InputStream is = t.getRequestBody();
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }

    private void sendResponse(HttpExchange t, int code, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        t.sendResponseHeaders(code, bytes.length);
        OutputStream os = t.getResponseBody();
        os.write(bytes);
        os.close();
    }
}
