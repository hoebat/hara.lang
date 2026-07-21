package hara.truffle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import hara.kernel.base.RT;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.Test;

public class HaraLanguageTest {
  @Test
  public void evaluatesLiteralsAndAddition() {
    try (Context context = context()) {
      assertEquals(42, context.eval(HaraLanguage.ID, "(+ 19 23)").asLong());
      assertEquals("hara", context.eval(HaraLanguage.ID, "\"hara\"").asString());
      assertTrue(context.eval(HaraLanguage.ID, "true").asBoolean());
      assertTrue(context.eval(HaraLanguage.ID, "nil").isNull());
      assertEquals(":ready", context.eval(HaraLanguage.ID, ":ready").toString());
    }
  }

  @Test
  public void evaluatesControlFlowAndSequentialBodies() {
    try (Context context = context()) {
      assertEquals(2, context.eval(HaraLanguage.ID, "(if false 1 2)").asLong());
      assertEquals(1, context.eval(HaraLanguage.ID, "(if 0 1 2)").asLong());
      assertTrue(context.eval(HaraLanguage.ID, "(if nil 1)").isNull());
      assertEquals(3, context.eval(HaraLanguage.ID, "(do 1 2 3)").asLong());
    }
  }

  @Test
  public void storesLexicalBindingsInFrames() {
    try (Context context = context()) {
      assertEquals(5, context.eval(HaraLanguage.ID, "(let [x 2 y 3] (+ x y))").asLong());
      assertEquals(2, context.eval(HaraLanguage.ID, "(let [x 1] (let [x 2] x))").asLong());

      PolyglotException error =
          assertThrows(
              PolyglotException.class, () -> context.eval(HaraLanguage.ID, "(let [x 1 y x] y)"));
      assertTrue(error.getMessage().contains("Unbound symbol: x"));
    }
  }

  @Test
  public void returnsPolyglotExecutableFunctions() {
    try (Context context = context()) {
      Value increment = context.eval(HaraLanguage.ID, "(fn [x] (+ x 1))");
      assertTrue(increment.canExecute());
      assertEquals(42, increment.execute(41).asLong());
      assertEquals(42, context.eval(HaraLanguage.ID, "((fn [x] (+ x 1)) 41)").asLong());
    }
  }

  @Test
  public void agreesWithTheExistingInterpreterForTheSupportedSlice() {
    RT.Instance<Object> interpreter = new RT.Instance<>(null, "truffle-differential-test");
    String[] expressions = {
      "(+ 19 23)",
      "(if false 1 2)",
      "(do 1 2 3)",
      "(let [x 2 y 3] (+ x y))",
      "((fn [x] (+ x 1)) 41)"
    };

    try (Context context = context()) {
      for (String expression : expressions) {
        Number expected = (Number) interpreter.eval(interpreter.readString(expression));
        assertEquals(expected.longValue(), context.eval(HaraLanguage.ID, expression).asLong());
      }
    }
  }

  @Test
  public void reportsLanguageErrorsAtThePolyglotBoundary() {
    try (Context context = context()) {
      PolyglotException unbound =
          assertThrows(PolyglotException.class, () -> context.eval(HaraLanguage.ID, "missing"));
      assertTrue(unbound.getMessage().contains("Unbound symbol: missing"));

      PolyglotException arity =
          assertThrows(
              PolyglotException.class, () -> context.eval(HaraLanguage.ID, "((fn [x] x) 1 2)"));
      assertTrue(arity.getMessage().contains("Expected 1 arguments, received 2"));

      PolyglotException capture =
          assertThrows(
              PolyglotException.class,
              () -> context.eval(HaraLanguage.ID, "(let [x 1] (fn [] x))"));
      assertTrue(capture.getMessage().contains("Unbound symbol: x"));
    }
  }

  private static Context context() {
    return Context.newBuilder(HaraLanguage.ID).build();
  }
}
