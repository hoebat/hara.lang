package hara.lang.kernel;

import hara.lang.base.Ex;
import hara.lang.base.G;
import hara.lang.lib.RT;
import hara.lang.kernel.redirect.FileRedirect;
import hara.lang.kernel.io.IRedirect;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@SuppressWarnings("rawtypes")
public class Main {

    public static void main(String[] args) throws IOException {
        var F = new Foundation();

        // Initialize RT
        var rt = new RT.Instance(F, "ROOT");
        F.RTS.put(rt._key, rt);

        if (args.length > 0) {
            String command = args[0];
            if ("--server".equals(command)) {
                startServer(F, rt);
            } else {
                runFile(rt, command);
            }
        } else {
            runRepl(rt);
        }
    }

    private static void startServer(Foundation F, RT.Instance rt) throws FileNotFoundException {
        var redirect = new FileRedirect("log/in.txt", "log/out.txt");
        var server = new Server(F, "PRIMARY", Foundation.DEFAULT_PORT, redirect);
        F.SERVERS.put(server._key, server);
        server.start();
    }

    private static void runFile(RT.Instance rt, String filepath) {
        try {
            String content = Files.readString(Path.of(filepath));
            Object ast = rt.readString(content);
            rt.eval(ast);
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
            System.exit(1);
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    private static void runRepl(RT.Instance rt) {
        System.out.println("Hara REPL (type 'exit' to quit)");
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            try {
                System.out.print("> ");
                System.out.flush();
                String line = reader.readLine();

                if (line == null) break; // EOF
                if ("exit".equals(line.trim())) break;
                if (line.trim().isEmpty()) continue;

                Object ast = rt.readString(line);
                Object result = rt.eval(ast);
                System.out.println(G.display(result));

            } catch (IOException e) {
                break;
            } catch (Throwable t) {
                System.out.println(G.display(t));
            }
        }
    }
}
