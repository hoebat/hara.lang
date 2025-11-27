package hara.lang.service;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import hara.lang.kernel.Foundation;

public class HttpTest {

    @Test
    public void testHttpServer() throws Exception {
        Foundation f = new Foundation();

        // Start HTTP Server on random port 8192
        List<String> startArgs = new ArrayList<>(Arrays.asList("SERVICE", "HTTP", "START", "8192"));
        Foundation.runCommand(f, startArgs);

        try {
            // 1. Check Info
            String info = get("http://localhost:8192/");
            assertEquals("Foundation HTTP Gateway Active", info);

            // 2. Create Session via HTTP with sufficient memory (1MB)
            String createResp = post("http://localhost:8192/session", "testSession 1000000");
            assertEquals("Created testSession", createResp);

            // 3. List Sessions
            String listResp = get("http://localhost:8192/session");
            assertTrue(listResp.contains("testSession"));

            // 4. Eval Code
            String evalResp = postEval("http://localhost:8192/eval", "testSession", "(+ 1 2)");
            assertEquals("3", evalResp);

        } finally {
            // Stop Server
            List<String> stopArgs = new ArrayList<>(Arrays.asList("SERVICE", "HTTP", "STOP"));
            Foundation.runCommand(f, stopArgs);
        }
    }

    private String get(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        return readStream(conn.getInputStream());
    }

    private String post(String urlStr, String body) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        writeStream(conn.getOutputStream(), body);
        return readStream(conn.getInputStream());
    }

    private String postEval(String urlStr, String session, String code) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("X-Session-ID", session);
        conn.setDoOutput(true);
        writeStream(conn.getOutputStream(), code);
        return readStream(conn.getInputStream());
    }

    private void writeStream(OutputStream os, String body) throws Exception {
        os.write(body.getBytes(StandardCharsets.UTF_8));
        os.flush();
        os.close();
    }

    private String readStream(InputStream is) throws Exception {
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
}
