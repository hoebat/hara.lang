package hara.lang.kernel;

import com.github.tonivade.claudb.ClauDB;
import com.github.tonivade.resp.RespServer;
import com.github.tonivade.claudb.DBServerContext;

import java.io.IOException;

public class Redis {

    private final RespServer server;
    private final int port;

    public Redis(String host, int port) {
        this.port = port;
        this.server = ClauDB.builder().host(host).port(port).build();
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop();
    }

    // Placeholder for future internal DB access if needed
    public com.github.tonivade.claudb.data.Database getDatabase(int index) {
        return null;
    }

    public void set(String key, String value) {
        try (hara.lang.kernel.Conn conn = new hara.lang.kernel.Conn(new java.net.Socket("localhost", port))) {
            conn.call("SET", key, value);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String get(String key) {
        try (hara.lang.kernel.Conn conn = new hara.lang.kernel.Conn(new java.net.Socket("localhost", port))) {
            Object result = conn.call("GET", key);
            if (result instanceof byte[]) return new String((byte[])result);
            if (result instanceof String) return (String)result;
            return null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
