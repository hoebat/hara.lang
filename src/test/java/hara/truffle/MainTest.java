package hara.truffle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class MainTest {
  @Test
  public void fileLibraryIsDefaultDeniedAndExplicitlyGranted() throws Exception {
    ByteArrayOutputStream deniedOutput = new ByteArrayOutputStream();
    ByteArrayOutputStream deniedError = new ByteArrayOutputStream();
    int denied =
        Main.run(
            new String[] {"eval", "(file/read \"denied.bin\")"},
            new PrintStream(deniedOutput, true, StandardCharsets.UTF_8),
            new PrintStream(deniedError, true, StandardCharsets.UTF_8));
    assertEquals(1, denied);
    assertTrue(deniedError.toString(StandardCharsets.UTF_8).contains("file access is denied"));

    Path path = Files.createTempFile("hara-runtime-library-", ".bin");
    Files.delete(path);
    try {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      ByteArrayOutputStream error = new ByteArrayOutputStream();
      String escaped = path.toString().replace("\\", "\\\\").replace("\"", "\\\"");
      String form =
          "(deref (file/write \""
              + escaped
              + "\" (bytes 1 -1))) "
              + "(bytes/get (deref (file/read \""
              + escaped
              + "\")) 1)";
      int status =
          Main.run(
              new String[] {"--allow-file", "eval", form},
              new PrintStream(output, true, StandardCharsets.UTF_8),
              new PrintStream(error, true, StandardCharsets.UTF_8));
      assertEquals(error.toString(StandardCharsets.UTF_8), 0, status);
      assertEquals("255\n", output.toString(StandardCharsets.UTF_8));
    } finally {
      Files.deleteIfExists(path);
    }
  }

  @Test
  public void evaluatesAnExpression() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ByteArrayOutputStream error = new ByteArrayOutputStream();

    int status =
        Main.run(
            new String[] {"eval", "(+ 19 23)"},
            new PrintStream(output, true, StandardCharsets.UTF_8),
            new PrintStream(error, true, StandardCharsets.UTF_8));

    assertEquals(0, status);
    assertEquals("42\n", output.toString(StandardCharsets.UTF_8));
    assertEquals("", error.toString(StandardCharsets.UTF_8));
  }

  @Test
  public void replLoadsCoreAndRendersLazyIterators() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ByteArrayOutputStream error = new ByteArrayOutputStream();
    int status =
        Main.run(
            new String[] {"repl"},
            new ByteArrayInputStream(
                "(inc 1)\n(map inc [1 2 3])\n".getBytes(StandardCharsets.UTF_8)),
            new PrintStream(output, true, StandardCharsets.UTF_8),
            new PrintStream(error, true, StandardCharsets.UTF_8));

    assertEquals(0, status);
    assertTrue(output.toString(StandardCharsets.UTF_8).contains("2\n"));
    assertTrue(output.toString(StandardCharsets.UTF_8).contains("#<lazy-iterator>\n"));
    assertEquals("", error.toString(StandardCharsets.UTF_8));
  }

  @Test
  public void runsThePackagedL0ConformanceCorpus() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ByteArrayOutputStream error = new ByteArrayOutputStream();

    int status =
        Main.run(
            new String[] {"conformance"},
            new PrintStream(output, true, StandardCharsets.UTF_8),
            new PrintStream(error, true, StandardCharsets.UTF_8));

    assertEquals(0, status);
    assertTrue(output.toString(StandardCharsets.UTF_8).contains("L0 conformance passed:"));
    assertEquals("", error.toString(StandardCharsets.UTF_8));
  }

  @Test
  public void reportsGuestErrorsWithoutAJavaStackTrace() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ByteArrayOutputStream error = new ByteArrayOutputStream();

    int status =
        Main.run(
            new String[] {"eval", "missing"},
            new PrintStream(output, true, StandardCharsets.UTF_8),
            new PrintStream(error, true, StandardCharsets.UTF_8));

    assertEquals(1, status);
    assertEquals("", output.toString(StandardCharsets.UTF_8));
    assertEquals("Unbound symbol: missing\n", error.toString(StandardCharsets.UTF_8));
  }

  @Test
  public void replRetainsContextAcrossInputsAndSupportsMultilineForms() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ByteArrayOutputStream error = new ByteArrayOutputStream();
    byte[] input =
        "(def answer 40)\n(+ answer 2)\n(let [x 1]\n  (+ x\n     2))\n:quit\n"
            .getBytes(StandardCharsets.UTF_8);

    int status =
        Main.run(
            new String[] {"repl"},
            new ByteArrayInputStream(input),
            new PrintStream(output, true, StandardCharsets.UTF_8),
            new PrintStream(error, true, StandardCharsets.UTF_8));

    assertEquals(0, status);
    assertTrue(output.toString(StandardCharsets.UTF_8).contains("#'user/answer\n42\n3\n"));
    assertEquals("", error.toString(StandardCharsets.UTF_8));
  }

  @Test
  public void replContinuesAfterGuestErrors() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ByteArrayOutputStream error = new ByteArrayOutputStream();
    byte[] input =
        "(def answer 40)\nmissing\n(+ answer 2)\n:quit\n".getBytes(StandardCharsets.UTF_8);

    int status =
        Main.run(
            new String[] {"repl"},
            new ByteArrayInputStream(input),
            new PrintStream(output, true, StandardCharsets.UTF_8),
            new PrintStream(error, true, StandardCharsets.UTF_8));

    assertEquals(0, status);
    assertTrue(output.toString(StandardCharsets.UTF_8).contains("42\n"));
    assertEquals("Unbound symbol: missing\n", error.toString(StandardCharsets.UTF_8));
  }

  @Test
  public void replRetainsCompletedFormHistory() {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    ByteArrayOutputStream error = new ByteArrayOutputStream();
    byte[] input = "(def answer 40)\nmissing\n:history\n:quit\n".getBytes(StandardCharsets.UTF_8);

    int status =
        Main.run(
            new String[] {"repl"},
            new ByteArrayInputStream(input),
            new PrintStream(output, true, StandardCharsets.UTF_8),
            new PrintStream(error, true, StandardCharsets.UTF_8));

    assertEquals(0, status);
    String history = output.toString(StandardCharsets.UTF_8);
    assertTrue(history.contains("1: (def answer 40)"));
    assertTrue(history.contains("2: missing"));
    assertEquals("Unbound symbol: missing\n", error.toString(StandardCharsets.UTF_8));
  }
}
