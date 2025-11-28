package hara.lang.kernel;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import hara.lang.lib.RT;

public class MainTest {

    @Test
    public void testRunRepl() {
        String input = "(+ 1 2)\nexit\n";
        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes());
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Foundation F = new Foundation();
        RT.Instance rt = new RT.Instance(F, "ROOT");

        Main.runRepl(rt, in, new PrintStream(out));

        String output = out.toString();
        assertTrue(output.contains("Hara REPL"));
        assertTrue(output.contains("3"));
    }

    @Test
    public void testRunFile() throws IOException {
         Path tempFile = Files.createTempFile("test_run_file", ".hara");
         Files.writeString(tempFile, "(def x 100)");

         Foundation F = new Foundation();
         RT.Instance rt = new RT.Instance(F, "ROOT");
         ByteArrayOutputStream err = new ByteArrayOutputStream();

         Main.runFile(rt, tempFile.toString(), new PrintStream(err));

         // Check if side effect happened (x defined in user env)
         // Need to check if 'x' resolves to 100 in rt
         Object result = rt.eval(rt.readString("x"));
         assertEquals(100L, result);

         Files.delete(tempFile);
    }
}
