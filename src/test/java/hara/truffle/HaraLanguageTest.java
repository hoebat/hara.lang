package hara.truffle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import hara.kernel.base.RT;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
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
  public void evaluatesSpecializedArithmeticOperations() {
    try (Context context = context()) {
      assertEquals(7, context.eval(HaraLanguage.ID, "(- 10 3)").asLong());
      assertEquals(42, context.eval(HaraLanguage.ID, "(* 6 7)").asLong());
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
  public void persistsDefinitionsAcrossEvaluations() {
    try (Context context = context()) {
      assertTrue(
          context.eval(HaraLanguage.ID, "(def answer 41)").toString().contains("user/answer"));
      assertEquals(42, context.eval(HaraLanguage.ID, "(+ answer 1)").asLong());
      context.eval(HaraLanguage.ID, "(def answer 42)");
      assertEquals(42, context.eval(HaraLanguage.ID, "answer").asLong());
    }
  }

  @Test
  public void evaluatesMultipleTopLevelFormsAndNamespaces() {
    try (Context context = context()) {
      assertEquals(3, context.eval(HaraLanguage.ID, "(def x 1) (+ x 2)").asLong());
      assertEquals(7, context.eval(HaraLanguage.ID, "(ns alpha) (def x 7) x").asLong());
      assertEquals(7, context.eval(HaraLanguage.ID, "alpha/x").asLong());

      context.eval(HaraLanguage.ID, "(ns beta)");
      PolyglotException missing =
          assertThrows(PolyglotException.class, () -> context.eval(HaraLanguage.ID, "x"));
      assertTrue(missing.getMessage().contains("Unbound symbol: x"));
      context.eval(HaraLanguage.ID, "(ns user)");
      assertEquals(1, context.eval(HaraLanguage.ID, "user/x").asLong());
    }
  }

  @Test
  public void isolatesDefinitionsBetweenContexts() {
    try (Context first = context();
        Context second = context()) {
      first.eval(HaraLanguage.ID, "(def only-here 1)");
      assertEquals(1, first.eval(HaraLanguage.ID, "only-here").asLong());
      PolyglotException missing =
          assertThrows(PolyglotException.class, () -> second.eval(HaraLanguage.ID, "only-here"));
      assertTrue(missing.getMessage().contains("Unbound symbol: only-here"));
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
  public void capturesLexicalBindingsInReturnedFunctions() {
    try (Context context = context()) {
      Value adder = context.eval(HaraLanguage.ID, "(let [x 41] (fn [y] (+ x y)))");
      assertTrue(adder.canExecute());
      assertEquals(42, adder.execute(1).asLong());
      assertEquals(
          42, context.eval(HaraLanguage.ID, "(((fn [x] (fn [y] (+ x y))) 40) 2)").asLong());
    }
  }

  @Test
  public void capturesTheCorrectShadowedBinding() {
    try (Context context = context()) {
      assertEquals(
          10,
          context
              .eval(HaraLanguage.ID, "(let [x 10] (let [f (fn [] x)] (let [x 20] (f))))")
              .asLong());
    }
  }

  @Test
  public void definesImmutableHostIndependentStructs() {
    try (Context context = context()) {
      Value person =
          context.eval(HaraLanguage.ID, "(defstruct Person [name age]) (Person \"Ada\" 36)");
      assertTrue(person.hasMembers());
      assertEquals("Ada", person.getMember("name").asString());
      assertEquals(36, person.getMember("age").asLong());
      assertTrue(person.toString().contains("Person"));
      assertEquals(
          "Ada", context.eval(HaraLanguage.ID, "(field (Person \"Ada\" 36) :name)").asString());
      assertTrue(context.eval(HaraLanguage.ID, "Person").canExecute());
    }
  }

  @Test
  public void extendsStructsWithLanguageProtocolsIncludingIFn() {
    try (Context context = context()) {
      assertEquals(
          43,
          context
              .eval(
                  HaraLanguage.ID,
                  "(defstruct Counter [base]) "
                      + "(defprotocol CounterOps (value [self]) (add [self amount])) "
                      + "(extend-type Counter CounterOps "
                      + "  (value [self] (field self :base)) "
                      + "  (add [self amount] (+ (field self :base) amount))) "
                      + "(protocol-call CounterOps add (Counter 41) 2)")
              .asLong());

      assertEquals(
          42,
          context
              .eval(
                  HaraLanguage.ID,
                  "(defstruct Incrementer [base]) "
                      + "(extend-type Incrementer IFn "
                      + "  (invoke [self value] (+ (field self :base) value))) "
                      + "((Incrementer 1) 41)")
              .asLong());
    }
  }

  @Test
  public void gatesExplicitHostInterop() {
    try (Context context = context()) {
      PolyglotException error =
          assertThrows(
              PolyglotException.class,
              () -> context.eval(HaraLanguage.ID, "(host-symbol \"java.lang.String\")"));
      assertTrue(error.getMessage().contains("Host interop is disabled"));
    }

    try (Context context =
        Context.newBuilder(HaraLanguage.ID)
            .allowHostAccess(HostAccess.ALL)
            .allowHostClassLookup(name -> true)
            .build()) {
      assertTrue(
          context
              .eval(HaraLanguage.ID, "(host-symbol \"java.lang.String\")")
              .toString()
              .contains("java.lang.String"));
    }
  }

  @Test
  public void expandsMacrosBeforeTruffleAnalysis() {
    try (Context context = context()) {
      context.eval(HaraLanguage.ID, "(defmacro unless [test body] `(if ~test nil ~body))");
      assertEquals(3, context.eval(HaraLanguage.ID, "(unless false (+ 1 2))").asLong());
      assertTrue(context.eval(HaraLanguage.ID, "(unless true (+ 1 2))").isNull());
    }
  }

  @Test
  public void expandsMacrosDefinedEarlierInTheSameSource() {
    try (Context context = context()) {
      assertEquals(
          42,
          context
              .eval(
                  HaraLanguage.ID,
                  "(defmacro when-not [test body] `(if ~test nil ~body)) "
                      + "(when-not false (+ 40 2))")
              .asLong());
    }
  }

  @Test
  public void supportsUnquoteSplicingInSyntaxQuotedForms() {
    try (Context context = context()) {
      context.eval(HaraLanguage.ID, "(defmacro do-all [forms] `(do ~@forms))");
      assertEquals(3, context.eval(HaraLanguage.ID, "(do-all (1 2 3))").asLong());
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
      assertTrue(unbound.getSourceLocation().isAvailable());
      assertEquals(1, unbound.getSourceLocation().getStartLine());
      assertTrue(unbound.getPolyglotStackTrace().iterator().hasNext());

      PolyglotException arity =
          assertThrows(
              PolyglotException.class, () -> context.eval(HaraLanguage.ID, "((fn [x] x) 1 2)"));
      assertTrue(arity.getMessage().contains("Expected 1 arguments, received 2"));

      assertEquals(1, context.eval(HaraLanguage.ID, "(let [x 1] ((fn [] x)))").asLong());
    }
  }

  private static Context context() {
    return Context.newBuilder(HaraLanguage.ID).build();
  }
}
