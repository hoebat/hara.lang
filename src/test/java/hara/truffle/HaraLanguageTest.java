package hara.truffle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import hara.kernel.base.RT;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
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
      assertEquals(0, context.eval(HaraLanguage.ID, "(+)").asLong());
      assertEquals(6, context.eval(HaraLanguage.ID, "(+ 1 2 3)").asLong());
      assertEquals(7, context.eval(HaraLanguage.ID, "(- 10 3)").asLong());
      assertEquals(-10, context.eval(HaraLanguage.ID, "(- 10)").asLong());
      assertEquals(1, context.eval(HaraLanguage.ID, "(*)").asLong());
      assertEquals(42, context.eval(HaraLanguage.ID, "(* 6 7)").asLong());
      assertEquals(24, context.eval(HaraLanguage.ID, "(* 2 3 4)").asLong());
      assertEquals(2, context.eval(HaraLanguage.ID, "(/ 5 2)").asLong());
      assertEquals(0, context.eval(HaraLanguage.ID, "(/ 2)").asLong());
      assertThrows(PolyglotException.class, () -> context.eval(HaraLanguage.ID, "(/)"));
    }
  }

  @Test
  public void evaluatesNumericComparisonsEqualityAndRemainder() {
    try (Context context = context()) {
      assertTrue(context.eval(HaraLanguage.ID, "(< 1 2)").asBoolean());
      assertTrue(context.eval(HaraLanguage.ID, "(<= 2 2)").asBoolean());
      assertTrue(context.eval(HaraLanguage.ID, "(> 3 2)").asBoolean());
      assertTrue(context.eval(HaraLanguage.ID, "(>= 3 3)").asBoolean());
      assertTrue(context.eval(HaraLanguage.ID, "(= 1 1.0)").asBoolean());
      assertTrue(context.eval(HaraLanguage.ID, "(not= 1 2)").asBoolean());
      assertEquals(1, context.eval(HaraLanguage.ID, "(mod 7 3)").asLong());
      assertTrue(context.eval(HaraLanguage.ID, "(< 1N 2N)").asBoolean());
      assertTrue(context.eval(HaraLanguage.ID, "(< 1 2 3)").asBoolean());
      assertTrue(!context.eval(HaraLanguage.ID, "(< 1 3 2)").asBoolean());
      assertTrue(context.eval(HaraLanguage.ID, "(< 1N 1.1M)").asBoolean());
      assertTrue(context.eval(HaraLanguage.ID, "(= 1N 1.0M)").asBoolean());
      assertTrue(context.eval(HaraLanguage.ID, "(= 1 1 1)").asBoolean());
      assertTrue(context.eval(HaraLanguage.ID, "(not= 1 1 2)").asBoolean());
      assertTrue(context.eval(HaraLanguage.ID, "(< 1.2M 1.3M)").asBoolean());
    }
  }

  @Test
  public void numericOverflowAndDivisionErrorsAreExplicit() {
    try (Context context = context()) {
      Value overflow = context.eval(HaraLanguage.ID, "(+ 9223372036854775807 1)");
      assertEquals(
          BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE), overflow.as(BigInteger.class));
      PolyglotException divideByZero =
          assertThrows(PolyglotException.class, () -> context.eval(HaraLanguage.ID, "(/ 1 0)"));
      assertTrue(divideByZero.getMessage().contains("Divide by zero"));
      PolyglotException remainderByZero =
          assertThrows(PolyglotException.class, () -> context.eval(HaraLanguage.ID, "(mod 1 0)"));
      assertTrue(remainderByZero.getMessage().contains("Divide by zero"));
    }
  }

  @Test
  public void numericSpecialValuesHaveStableComparisonBehavior() {
    try (Context context = context()) {
      assertTrue(context.eval(HaraLanguage.ID, "(= ##NaN ##NaN)").asBoolean());
      assertTrue(context.eval(HaraLanguage.ID, "(not= ##NaN 1)").asBoolean());
      assertTrue(context.eval(HaraLanguage.ID, "(= -0.0 0.0)").asBoolean());
      assertTrue(context.eval(HaraLanguage.ID, "(= 1.0M 1.00M)").asBoolean());
      assertTrue(context.eval(HaraLanguage.ID, "(= ##Inf ##Inf)").asBoolean());
      assertTrue(context.eval(HaraLanguage.ID, "(< 1 ##Inf)").asBoolean());
      assertTrue(context.eval(HaraLanguage.ID, "(not= ##Inf 1)").asBoolean());
      assertTrue(context.eval(HaraLanguage.ID, "(not false)").asBoolean());
      assertTrue(!context.eval(HaraLanguage.ID, "(not true)").asBoolean());
      assertTrue(context.eval(HaraLanguage.ID, "(not nil)").asBoolean());
      assertTrue(context.eval(HaraLanguage.ID, "(and true 7)").asLong() == 7);
      assertTrue(context.eval(HaraLanguage.ID, "(or false 8)").asLong() == 8);
      assertTrue(context.eval(HaraLanguage.ID, "(and false (/ 1 0))").asBoolean() == false);
      assertTrue(context.eval(HaraLanguage.ID, "(or true (/ 1 0))").asBoolean());
      assertTrue(context.eval(HaraLanguage.ID, "(when true 4 5)").asLong() == 5);
      assertTrue(context.eval(HaraLanguage.ID, "(when false (/ 1 0))").isNull());
      assertTrue(context.eval(HaraLanguage.ID, "(when-not false 6)").asLong() == 6);
    }
  }

  @Test
  public void constructsBytesWithTheOrdinaryBytesForm() {
    try (Context context = context()) {
      Value bytes = context.eval(HaraLanguage.ID, "(bytes 1 2 -3)");
      assertTrue(bytes.hasArrayElements());
      assertEquals(3, bytes.getArraySize());
      assertEquals(1, bytes.getArrayElement(0).asLong());
      assertEquals(2, bytes.getArrayElement(1).asLong());
      assertEquals(-3, bytes.getArrayElement(2).asLong());
      assertEquals(
          3, context.eval(HaraLanguage.ID, "(protocol-call ICount count (bytes 1 2 -3))").asLong());
      assertEquals(
          2, context.eval(HaraLanguage.ID, "(protocol-call INth nth (bytes 1 2 -3) 1)").asLong());
      assertEquals(
          7,
          context
              .eval(HaraLanguage.ID, "(protocol-call ILookup lookup (bytes 1 2 -3) 8 7)")
              .asLong());
      bytes.setArrayElement(0, 9);
      assertEquals(9, bytes.getArrayElement(0).asLong());
    }
  }

  @Test
  public void constructsAndMutatesExplicitXtalkTargetValues() {
    try (Context context = context()) {
      Value array = context.eval(HaraLanguage.ID, "(x:array 1 2)");
      assertTrue(array.hasArrayElements());
      array.setArrayElement(1, 7);
      assertEquals(7, array.getArrayElement(1).asLong());

      Value object = context.eval(HaraLanguage.ID, "(x:object :answer 41)");
      assertTrue(object.hasHashEntries());
      object.putHashEntry(":answer", 42);
      assertEquals(42, object.getHashValue(":answer").asLong());
      assertEquals(2, context.eval(HaraLanguage.ID, "(x:len (x:array 1 2))").asLong());
      assertEquals(
          7,
          context
              .eval(HaraLanguage.ID, "(let [a (x:array 1 2)] (x:set a 1 7) (x:get a 1))")
              .asLong());
      assertEquals(
          1,
          context
              .eval(HaraLanguage.ID, "(let [a (x:array 1 2)] (x:delete a 0) (x:len a))")
              .asLong());
      assertEquals(
          3,
          context
              .eval(HaraLanguage.ID, "(let [a (x:array 1 2)] (x:append a 3) (x:len a))")
              .asLong());
      assertEquals(
          7,
          context
              .eval(HaraLanguage.ID, "(let [a (x:array 1 2)] (x:insert a 1 7) (x:get a 1))")
              .asLong());
      assertEquals(
          2, context.eval(HaraLanguage.ID, "(x:len (x:slice (x:array 1 2 3) 1 3))").asLong());
      assertEquals(2, context.eval(HaraLanguage.ID, "(x:get (x:clone (x:array 1 2)) 1)").asLong());
    }
  }

  @Test
  public void convertsBetweenNumericRepresentationsExplicitly() {
    try (Context context = context()) {
      assertEquals(
          BigInteger.ONE, context.eval(HaraLanguage.ID, "(bigint 1.9)").as(BigInteger.class));

      Value decimal = context.eval(HaraLanguage.ID, "(bigdec 1.2300)");
      assertTrue(decimal.hasMembers());
      assertEquals("1.23", decimal.getMember("value").asString());

      assertEquals(2.25, context.eval(HaraLanguage.ID, "(+ (double 1.25M) 1.0)").asDouble(), 0.0);
      assertThrows(PolyglotException.class, () -> context.eval(HaraLanguage.ID, "(+ 1.25M 1.0)"));
      assertThrows(PolyglotException.class, () -> context.eval(HaraLanguage.ID, "(bigint \"1\")"));
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
  public void evaluatesLoopAndTailRecur() {
    try (Context context = context()) {
      assertEquals(
          42,
          context.eval(HaraLanguage.ID, "(loop [value 41] (if value (recur nil) 42))").asLong());
    }
  }

  @Test
  public void rejectsRecurOutsideLoopAndNonTailRecur() {
    try (Context context = context()) {
      PolyglotException outside =
          assertThrows(PolyglotException.class, () -> context.eval(HaraLanguage.ID, "(recur)"));
      assertTrue(outside.getMessage().contains("outside loop"));

      PolyglotException nonTail =
          assertThrows(
              PolyglotException.class,
              () -> context.eval(HaraLanguage.ID, "(loop [value true] (+ (recur nil) 1))"));
      assertTrue(nonTail.getMessage().contains("tail position"));
    }
  }

  @Test
  public void evaluatesThrowCatchAndFinally() {
    try (Context context = context()) {
      assertEquals(
          42,
          context
              .eval(
                  HaraLanguage.ID,
                  "(try (throw 41) "
                      + "(catch Exception error (+ error 1)) "
                      + "(finally (def cleaned true)))")
              .asLong());
      assertTrue(context.eval(HaraLanguage.ID, "cleaned").asBoolean());
    }
  }

  @Test
  public void supportsOrderedTypedGuestCatchClauses() {
    try (Context context = context()) {
      assertEquals(
          7,
          context
              .eval(
                  HaraLanguage.ID,
                  "(defstruct Problem [value]) "
                      + "(try (throw (Problem 7)) "
                      + "(catch Other error 0) "
                      + "(catch Problem error (field error :value)))")
              .asLong());
      PolyglotException unmatched =
          assertThrows(
              PolyglotException.class,
              () ->
                  context.eval(
                      HaraLanguage.ID, "(try (throw (Problem 9)) (catch Other error error))"));
      assertTrue(unmatched.isGuestException());
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
  public void loadsHaraSourceIntoTheCurrentContext() throws Exception {
    try (Context context = context()) {
      assertEquals(
          42,
          context.eval(HaraLanguage.ID, "(load-string \"(def loaded 41)\") (+ loaded 1)").asLong());

      Path file = Files.createTempFile("hara-l0-", ".hara");
      try {
        Files.writeString(file, "(def from-file 42)");
        String path = file.toString().replace("\\", "\\\\").replace("\"", "\\\"");
        context.eval(HaraLanguage.ID, "(load-file \"" + path + "\")");
        assertEquals(42, context.eval(HaraLanguage.ID, "from-file").asLong());
      } finally {
        Files.deleteIfExists(file);
      }
    }
  }

  @Test
  public void failedModuleEvaluationRollsBackVarsMacrosAndNamespace() throws Exception {
    try (Context context = context()) {
      context.eval(HaraLanguage.ID, "(def stable 7)");
      PolyglotException stringFailure =
          assertThrows(
              PolyglotException.class,
              () ->
                  context.eval(
                      HaraLanguage.ID,
                      "(load-string \"(ns transient) (def leaked 9) (throw :failed)\")"));
      assertTrue(stringFailure.getMessage().contains("Unable to evaluate Hara source"));
      assertEquals(7, context.eval(HaraLanguage.ID, "stable").asLong());
      assertTrue(
          assertThrows(PolyglotException.class, () -> context.eval(HaraLanguage.ID, "leaked"))
              .getMessage()
              .contains("Unbound symbol"));

      Path file = Files.createTempFile("hara-l0-failing-", ".hara");
      try {
        Files.writeString(file, "(def file-leaked 10) (throw :failed-file)");
        String path = file.toString().replace("\\", "\\\\").replace("\"", "\\\"");
        assertThrows(
            PolyglotException.class,
            () -> context.eval(HaraLanguage.ID, "(load-file \"" + path + "\")"));
        assertTrue(
            assertThrows(
                    PolyglotException.class, () -> context.eval(HaraLanguage.ID, "file-leaked"))
                .getMessage()
                .contains("Unbound symbol"));
      } finally {
        Files.deleteIfExists(file);
      }
    }
  }

  @Test
  public void requireCachesCanonicalModulesAndLoadFileIncrementsRevision() throws Exception {
    try (Context context = context()) {
      Path file = Files.createTempFile("hara-l0-module-", ".hara");
      try {
        Files.writeString(file, "(def module-answer 41)");
        String path = file.toString().replace("\\", "\\\\").replace("\"", "\\\"");
        context.eval(HaraLanguage.ID, "(require \"" + path + "\")");
        assertEquals(41, context.eval(HaraLanguage.ID, "module-answer").asLong());
        assertEquals(
            1, context.eval(HaraLanguage.ID, "(module-revision \"" + path + "\")").asLong());

        Files.writeString(file, "(def module-answer 42)");
        context.eval(HaraLanguage.ID, "(require \"" + path + "\")");
        assertEquals(41, context.eval(HaraLanguage.ID, "module-answer").asLong());
        assertEquals(
            1, context.eval(HaraLanguage.ID, "(module-revision \"" + path + "\")").asLong());

        context.eval(HaraLanguage.ID, "(load-file \"" + path + "\")");
        assertEquals(42, context.eval(HaraLanguage.ID, "module-answer").asLong());
        assertEquals(
            2, context.eval(HaraLanguage.ID, "(module-revision \"" + path + "\")").asLong());
        Files.writeString(file, "(def module-answer 43)");
        context.eval(HaraLanguage.ID, "(require \"" + path + "\" {:reload true})");
        assertEquals(43, context.eval(HaraLanguage.ID, "module-answer").asLong());
        assertEquals(
            3, context.eval(HaraLanguage.ID, "(module-revision \"" + path + "\")").asLong());
      } finally {
        Files.deleteIfExists(file);
      }
    }
  }

  @Test
  public void requirePreservesCallerNamespaceAndSupportsAliases() throws Exception {
    try (Context context = context()) {
      Path file = Files.createTempFile("hara-l0-alias-", ".hara");
      try {
        Files.writeString(file, "(ns library) (def answer 42)");
        String path = file.toString().replace("\\", "\\\\").replace("\"", "\\\"");
        assertEquals(
            42,
            context
                .eval(HaraLanguage.ID, "(require \"" + path + "\" {:as 'lib}) lib/answer")
                .asLong());
        assertEquals(
            1, context.eval(HaraLanguage.ID, "(module-revision \"" + path + "\")").asLong());
        context.eval(HaraLanguage.ID, "(def caller-value 7)");
        assertEquals(7, context.eval(HaraLanguage.ID, "caller-value").asLong());
      } finally {
        Files.deleteIfExists(file);
      }
    }
  }

  @Test
  public void requireRejectsCyclesAndRollsBackPartialModules() throws Exception {
    try (Context context = context()) {
      Path directory = Files.createTempDirectory("hara-l0-cycle-");
      Path first = directory.resolve("first.hara");
      Path second = directory.resolve("second.hara");
      try {
        String firstPath = first.toString().replace("\\", "\\\\").replace("\"", "\\\"");
        String secondPath = second.toString().replace("\\", "\\\\").replace("\"", "\\\"");
        Files.writeString(first, "(def first-value 1) (require \"" + secondPath + "\")");
        Files.writeString(second, "(def second-value 2) (require \"" + firstPath + "\")");
        PolyglotException cycle =
            assertThrows(
                PolyglotException.class,
                () -> context.eval(HaraLanguage.ID, "(require \"" + firstPath + "\")"));
        assertTrue(cycle.getMessage().contains("Cyclic module require"));
        assertTrue(
            assertThrows(
                    PolyglotException.class, () -> context.eval(HaraLanguage.ID, "first-value"))
                .getMessage()
                .contains("Unbound symbol"));
        assertTrue(
            assertThrows(
                    PolyglotException.class, () -> context.eval(HaraLanguage.ID, "second-value"))
                .getMessage()
                .contains("Unbound symbol"));
      } finally {
        Files.deleteIfExists(first);
        Files.deleteIfExists(second);
        Files.deleteIfExists(directory);
      }
    }
  }

  @Test
  public void requireRecordsDeterministicModuleDependencies() throws Exception {
    try (Context context = context()) {
      Path directory = Files.createTempDirectory("hara-l0-deps-");
      Path child = directory.resolve("child.hara");
      Path parent = directory.resolve("parent.hara");
      try {
        String childPath = child.toString().replace("\\", "\\\\").replace("\"", "\\\"");
        String parentPath = parent.toString().replace("\\", "\\\\").replace("\"", "\\\"");
        Files.writeString(child, "(def child-value 7)");
        Files.writeString(parent, "(require \"" + childPath + "\") (def parent-value 8)");
        context.eval(HaraLanguage.ID, "(require \"" + parentPath + "\")");
        assertEquals(
            child.toAbsolutePath().normalize().toString(),
            context
                .eval(HaraLanguage.ID, "(x:get (module-dependencies \"" + parentPath + "\") 0)")
                .asString());
      } finally {
        Files.deleteIfExists(parent);
        Files.deleteIfExists(child);
        Files.deleteIfExists(directory);
      }
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
  public void resolvesQualifiedSymbolsThroughContextLocalAliases() {
    try (Context context = context()) {
      assertEquals(
          42,
          context
              .eval(
                  HaraLanguage.ID,
                  "(ns target) (def answer 42) (ns user) (alias t target) t/answer")
              .asLong());
      PolyglotException missing =
          assertThrows(
              PolyglotException.class, () -> context.eval(HaraLanguage.ID, "(alias x absent)"));
      assertTrue(missing.getMessage().contains("missing namespace"));
    }
  }

  @Test
  public void refersNamespaceValuesIntoCurrentNamespace() {
    try (Context context = context()) {
      assertEquals(
          42,
          context
              .eval(
                  HaraLanguage.ID,
                  "(ns source) (def answer 42) (ns user) (refer \"source\") answer")
              .asLong());
      PolyglotException missing =
          assertThrows(
              PolyglotException.class, () -> context.eval(HaraLanguage.ID, "(refer \"absent\")"));
      assertTrue(missing.getMessage().contains("missing namespace"));
    }
  }

  @Test
  public void refersLiveVarIdentityAcrossNamespaces() {
    try (Context context = context()) {
      assertEquals(
          2,
          context
              .eval(
                  HaraLanguage.ID,
                  "(ns source) (def answer 1) (ns user) (refer \"source\") "
                      + "(in-ns 'source) (set! answer 2) (in-ns 'user) answer")
              .asLong());
    }
  }

  @Test
  public void altersVarRootThroughAnHaraFunction() {
    try (Context context = context()) {
      assertEquals(
          41,
          context
              .eval(
                  HaraLanguage.ID,
                  "(def answer 1) (defn add [x y] (+ x y)) "
                      + "(alter-var-root (var answer) add 40) answer")
              .asLong());
      assertEquals(41, context.eval(HaraLanguage.ID, "answer").asLong());
    }
  }

  @Test
  public void appliesFunctionsWithAFinalSequentialArgument() {
    try (Context context = context()) {
      assertEquals(
          6,
          context
              .eval(HaraLanguage.ID, "(defn sum3 [a b c] (+ a b c)) (apply sum3 1 [2 3])")
              .asLong());
      assertEquals(
          1,
          context
              .eval(HaraLanguage.ID, "(defn first-rest [x & xs] x) (apply first-rest [1 2 3 4])")
              .asLong());
    }
  }

  @Test
  public void supportsInNsAndUseAsOrdinaryRuntimeForms() {
    try (Context context = context()) {
      assertEquals(
          42,
          context
              .eval(
                  HaraLanguage.ID,
                  "(in-ns 'source) (def answer 42) (in-ns 'user) (use 'source) answer")
              .asLong());
      PolyglotException invalid =
          assertThrows(PolyglotException.class, () -> context.eval(HaraLanguage.ID, "(in-ns 1)"));
      assertTrue(invalid.getMessage().contains("unqualified namespace symbol"));
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
  public void destructuresSequentialFunctionArguments() {
    try (Context context = context()) {
      assertEquals(
          42,
          context.eval(HaraLanguage.ID, "((fn [[left right]] (+ left right)) [19 23])").asLong());
    }
  }

  @Test
  public void supportsVariadicFunctionRestArguments() {
    try (Context context = context()) {
      assertEquals(
          42, context.eval(HaraLanguage.ID, "((fn [value & more] value) 42 1 2)").asLong());
      PolyglotException error =
          assertThrows(
              PolyglotException.class,
              () -> context.eval(HaraLanguage.ID, "((fn [value & more] value))"));
      assertTrue(error.getMessage().contains("at least 1 arguments"));
    }
  }

  @Test
  public void dispatchesMultiArityFnAndDefnClauses() {
    try (Context context = context()) {
      assertEquals(
          42,
          context
              .eval(
                  HaraLanguage.ID,
                  "(defn choose "
                      + "([value] value) "
                      + "([left right] (+ left right))) "
                      + "(choose 19 23)")
              .asLong());
      assertEquals(41, context.eval(HaraLanguage.ID, "(choose 41)").asLong());
    }
  }

  @Test
  public void destructuresMapFunctionArguments() {
    try (Context context = context()) {
      assertEquals(
          42, context.eval(HaraLanguage.ID, "((fn [{:keys [age]}] (+ age 1)) {:age 41})").asLong());
      assertEquals(
          42,
          context
              .eval(HaraLanguage.ID, "((fn [{:keys [age] :or {age 41}}] (+ age 1)) {})")
              .asLong());
    }
  }

  @Test
  public void destructuresLetAndLoopBindingsIncludingNestedRest() {
    try (Context context = context()) {
      assertEquals(
          42,
          context
              .eval(HaraLanguage.ID, "(let [[left & rest] [19 23]] (if rest (+ left 23) 0))")
              .asLong());
      assertEquals(
          42, context.eval(HaraLanguage.ID, "(let [{:keys [age]} {:age 41}] (+ age 1))").asLong());
      assertEquals(
          42,
          context
              .eval(HaraLanguage.ID, "(loop [[left & rest] [19 23]] (if rest (+ left 23) 0))")
              .asLong());
    }
  }

  @Test
  public void destructuringTreatsMissingValuesAndNilSourcesAsNil() {
    try (Context context = context()) {
      assertEquals(
          41,
          context.eval(HaraLanguage.ID, "(let [[a b] [1]] (+ (if a 1 0) (if b 40 40)))").asLong());
      assertEquals(2, context.eval(HaraLanguage.ID, "(let [{:keys [a]} nil] (if a 1 2))").asLong());
      assertEquals(
          2, context.eval(HaraLanguage.ID, "(let [[a & rest] nil] (if rest 1 2))").asLong());
    }
  }

  @Test
  public void definesOrdinaryFunctionsWithOptionalDocumentationAndAttributes() {
    try (Context context = context()) {
      assertEquals(
          42,
          context
              .eval(
                  HaraLanguage.ID,
                  "(defn increment \"increments\" {:private true} [x] (+ x 1)) " + "(increment 41)")
              .asLong());
    }
  }

  @Test
  public void resolvesRecursiveDefnCallsThroughTheCurrentNamespace() {
    try (Context context = context()) {
      assertEquals(
          42,
          context
              .eval(
                  HaraLanguage.ID,
                  "(defn recurse-once [value] "
                      + "  (if value (recurse-once nil) 42)) "
                      + "(recurse-once true)")
              .asLong());
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
  public void structsCarryLanguageNativeMetadata() {
    try (Context context = context()) {
      Value result =
          context.eval(
              HaraLanguage.ID,
              "(defstruct Person [name]) "
                  + "(protocol-call ILookup lookup "
                  + "  (protocol-call IObjType meta "
                  + "    (protocol-call IObjType with-meta (Person \"Ada\") {:doc \"person\"})) "
                  + "  :doc)");
      assertEquals("person", result.asString());
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
  public void cachesProtocolDispatchByReceiverShapeAndInvalidatesExtensions() {
    try (Context context = context()) {
      assertEquals(
          "Ada",
          context
              .eval(
                  HaraLanguage.ID,
                  "(defprotocol Describable (describe [self])) "
                      + "(defstruct Person [name]) "
                      + "(defstruct NumberValue [value]) "
                      + "(extend-type Person Describable "
                      + "  (describe [self] (field self :name))) "
                      + "(extend-type NumberValue Describable "
                      + "  (describe [self] (field self :value))) "
                      + "(def describe-value "
                      + "  (fn [value] (protocol-call Describable describe value))) "
                      + "(describe-value (Person \"Ada\"))")
              .asString());
      assertEquals(42, context.eval(HaraLanguage.ID, "(describe-value (NumberValue 42))").asLong());

      assertEquals(
          2,
          context
              .eval(
                  HaraLanguage.ID,
                  "(extend-type Person Describable (describe [self] 2)) "
                      + "(describe-value (Person \"Ada\"))")
              .asLong());
    }
  }

  @Test
  public void extendsStructsWithCoreAdapterProtocols() {
    try (Context context = context()) {
      assertEquals(
          41,
          context
              .eval(
                  HaraLanguage.ID,
                  "(defstruct Box [size]) "
                      + "(extend-type Box ICount "
                      + "  (count [self] (field self :size))) "
                      + "(protocol-call ICount count (Box 41))")
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
