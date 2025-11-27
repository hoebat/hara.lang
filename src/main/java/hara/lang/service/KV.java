package hara.lang.service;

import hara.lang.kernel.Conn;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.Collections;

public class KV {

    private final ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();
    private final String filename;
    private FileOutputStream fileOut;
    private Conn.Encoder encoder;

    public KV(String filename) {
        this.filename = filename;
    }

    private String toString(Object o) {
        if (o instanceof byte[]) {
            return new String((byte[]) o, StandardCharsets.UTF_8);
        }
        return String.valueOf(o);
    }

    public void load() {
        File f = new File(filename);
        // System.out.println("DEBUG: Loading from " + f.getAbsolutePath() + ", exists: " + f.exists() + ", length: " + f.length());

        if (!f.exists()) {
            try {
                // Open for appending immediately
                fileOut = new FileOutputStream(f, true);
                encoder = new Conn.Encoder(new BufferedOutputStream(fileOut));
            } catch (IOException e) {
                throw new RuntimeException("Could not create KV store file: " + filename, e);
            }
            return;
        }

        try (FileInputStream fis = new FileInputStream(f);
             BufferedInputStream bis = new BufferedInputStream(fis)) {

            Conn.Parser parser = new Conn.Parser(bis);
            // int count = 0;
            while (true) {
                Object obj = parser.parse();
                if (obj == null) break;

                if (obj instanceof List) {
                    List<?> cmd = (List<?>) obj;
                    // System.out.println("DEBUG: Loaded cmd: " + cmd);
                    if (cmd.isEmpty()) continue;

                    String op = toString(cmd.get(0));
                    if ("SET".equals(op) && cmd.size() >= 3) {
                        store.put(toString(cmd.get(1)), toString(cmd.get(2)));
                    } else if ("DEL".equals(op) && cmd.size() >= 2) {
                        store.remove(toString(cmd.get(1)));
                    }
                    // count++;
                }
            }
            // System.out.println("DEBUG: Loaded " + count + " commands.");

            // Open for appending
            fileOut = new FileOutputStream(f, true);
            encoder = new Conn.Encoder(new BufferedOutputStream(fileOut));

        } catch (IOException e) {
             throw new RuntimeException("Failed to load KV store from: " + filename, e);
        }
    }

    public synchronized String set(String key, String value) {
        store.put(key, value);
        appendLog("SET", key, value);
        return "OK";
    }

    public String get(String key) {
        return store.get(key);
    }

    public synchronized Long del(String key) {
        String val = store.remove(key);
        if (val != null) {
            appendLog("DEL", key);
            return 1L;
        }
        return 0L;
    }

    public List<String> keys() {
        return new ArrayList<>(store.keySet());
    }

    private void appendLog(Object... args) {
        if (encoder == null) return;
        try {
            encoder.write(Arrays.asList(args));
            encoder.flush();
        } catch (IOException e) {
            e.printStackTrace(); // TODO: Better error handling?
        }
    }

    public void close() {
        if (fileOut != null) {
            try {
                fileOut.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
