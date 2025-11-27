package hara.lang.lib;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import hara.lang.base.Ex;
import hara.lang.base.Module;
import hara.lang.data.Map;

public interface IO {

    @Module.Ns(name = "global", tag = "io")
    public interface Primitives {

        @Module.Fn(name = "slurp", complete = true)
        public static String slurp(String path) {
            try {
                return Files.readString(Path.of(path));
            } catch (IOException e) {
                // Ex.Runtime only takes a String, so we include the message
                throw new Ex.Runtime("Failed to slurp: " + path + " " + e.getMessage());
            }
        }

        @Module.Fn(name = "spit", complete = true)
        public static void spit(String path, String content) {
            try {
                Files.writeString(Path.of(path), content);
            } catch (IOException e) {
                throw new Ex.Runtime("Failed to spit: " + path + " " + e.getMessage());
            }
        }

        @Module.Fn(name = "sh", vargs = true, complete = true)
        public static Map.Standard sh(Object args) {
             // Cast the Object array to String array
             Object[] objArgs = hara.lang.base.It.toArray(hara.lang.base.It.iter(args));
             String[] strArgs = new String[objArgs.length];
             for(int i=0; i<objArgs.length; i++) {
                 strArgs[i] = String.valueOf(objArgs[i]);
             }
             return shImpl(strArgs);
        }

        private static String readStream(InputStream is) {
            return new BufferedReader(new InputStreamReader(is))
                    .lines().collect(Collectors.joining("\n"));
        }

        private static Map.Standard shImpl(String[] command) {
             try {
                ProcessBuilder pb = new ProcessBuilder(command);
                Process process = pb.start();

                CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(
                        () -> readStream(process.getInputStream()));

                CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(
                        () -> readStream(process.getErrorStream()));

                int exitCode = process.waitFor();

                String stdout = stdoutFuture.join();
                String stderr = stderrFuture.join();

                return Builtin.Struct.hashMap(
                    hara.lang.base.Arr.objects(
                        Builtin.Basic.keyword("exit"), exitCode,
                        Builtin.Basic.keyword("out"), stdout,
                        Builtin.Basic.keyword("err"), stderr
                    )
                );

            } catch (IOException | InterruptedException e) {
                 throw new Ex.Runtime("Failed to execute shell command: " + e.getMessage());
            }
        }
    }
}
