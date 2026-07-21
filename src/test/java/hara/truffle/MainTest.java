package hara.truffle;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class MainTest {
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
}
