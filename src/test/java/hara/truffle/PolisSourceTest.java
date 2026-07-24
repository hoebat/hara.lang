package hara.truffle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.graalvm.polyglot.Context;
import org.junit.Test;

public class PolisSourceTest {
  @Test
  public void loadsPackagedProvenanceNamespace() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      context.eval(HaraLanguage.ID, "(require 'polis.common.provenance)");
      assertEquals(
          "demo.core",
          context
              .eval(
                  HaraLanguage.ID,
                  "(polis.common.provenance/module-id {:id 'demo.core})")
              .toString());
    }
  }

  @Test
  public void runsTranslatedProvenanceFacts() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      context.eval(HaraLanguage.ID, "(require 'polis.common.provenance-test)");
      var results =
          context.eval(
              HaraLanguage.ID,
              "(code.test/run {:namespace \"polis.common.provenance-test\"})");
      assertEquals(12, results.getArraySize());
      for (long i = 0; i < results.getArraySize(); i++) {
        assertTrue(
            results.getArrayElement(i).toString(),
            "PASS".equals(results.getArrayElement(i).getHashValue("status").asString()));
      }
    }
  }

  @Test
  public void runsTranslatedUtilityFacts() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      context.eval(HaraLanguage.ID, "(require 'polis.common.util-test)");
      var results =
          context.eval(
              HaraLanguage.ID,
              "(code.test/run {:namespace \"polis.common.util-test\"})");
      assertEquals(17, results.getArraySize());
      for (long i = 0; i < results.getArraySize(); i++) {
        assertTrue(
            results.getArrayElement(i).toString(),
            "PASS".equals(results.getArrayElement(i).getHashValue("status").asString()));
      }
    }
  }

  @Test
  public void runsTranslatedGrammarSpecFacts() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      context.eval(HaraLanguage.ID, "(require 'polis.common.grammar-spec-test)");
      var results =
          context.eval(
              HaraLanguage.ID,
              "(code.test/run {:namespace \"polis.common.grammar-spec-test\"})");
      assertEquals(6, results.getArraySize());
      for (long i = 0; i < results.getArraySize(); i++) {
        assertTrue(
            results.getArrayElement(i).toString(),
            "PASS".equals(results.getArrayElement(i).getHashValue("status").asString()));
      }
    }
  }

  @Test
  public void runsTranslatedGrammarMacroFacts() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      context.eval(HaraLanguage.ID, "(require 'polis.common.grammar-macro-test)");
      var results =
          context.eval(
              HaraLanguage.ID,
              "(code.test/run {:namespace \"polis.common.grammar-macro-test\"})");
      assertEquals(13, results.getArraySize());
      for (long i = 0; i < results.getArraySize(); i++) {
        assertTrue(
            results.getArrayElement(i).toString(),
            "PASS".equals(results.getArrayElement(i).getHashValue("status").asString()));
      }
    }
  }

  @Test
  public void runsTranslatedPreprocessBaseFacts() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      context.eval(HaraLanguage.ID, "(require 'polis.common.preprocess-base-test)");
      var results =
          context.eval(
              HaraLanguage.ID,
              "(code.test/run {:namespace \"polis.common.preprocess-base-test\"})");
      assertEquals(4, results.getArraySize());
      for (long i = 0; i < results.getArraySize(); i++) {
        assertTrue(
            results.getArrayElement(i).toString(),
            "PASS".equals(results.getArrayElement(i).getHashValue("status").asString()));
      }
    }
  }

  @Test
  public void runsTranslatedGrammarXtalkFacts() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      context.eval(HaraLanguage.ID, "(require 'polis.common.grammar-xtalk-test)");
      var results =
          context.eval(
              HaraLanguage.ID,
              "(code.test/run {:namespace \"polis.common.grammar-xtalk-test\"})");
      assertEquals(49, results.getArraySize());
      for (long i = 0; i < results.getArraySize(); i++) {
        assertTrue(
            results.getArrayElement(i).toString(),
            "PASS".equals(results.getArrayElement(i).getHashValue("status").asString()));
      }
    }
  }

  @Test
  public void runsTranslatedGrammarXtalkSystemFacts() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      context.eval(HaraLanguage.ID, "(require 'polis.common.grammar-xtalk-system-test)");
      var results =
          context.eval(
              HaraLanguage.ID,
              "(code.test/run {:namespace \"polis.common.grammar-xtalk-system-test\"})");
      assertEquals(24, results.getArraySize());
      for (long i = 0; i < results.getArraySize(); i++) {
        assertTrue(
            results.getArrayElement(i).toString(),
            "PASS".equals(results.getArrayElement(i).getHashValue("status").asString()));
      }
    }
  }

  @Test
  public void runsTranslatedGrammarFacts() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      context.eval(HaraLanguage.ID, "(require 'polis.common.grammar-test)");
      var results =
          context.eval(
              HaraLanguage.ID,
              "(code.test/run {:namespace \"polis.common.grammar-test\"})");
      assertEquals(19, results.getArraySize());
      for (long i = 0; i < results.getArraySize(); i++) {
        assertTrue(
            results.getArrayElement(i).toString(),
            "PASS".equals(results.getArrayElement(i).getHashValue("status").asString()));
      }
    }
  }

  @Test
  public void runsTranslatedEmitHelperFacts() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      context.eval(HaraLanguage.ID, "(require 'polis.common.emit-helper-test)");
      var results =
          context.eval(
              HaraLanguage.ID,
              "(code.test/run {:namespace \"polis.common.emit-helper-test\"})");
      assertEquals(12, results.getArraySize());
      for (long i = 0; i < results.getArraySize(); i++) {
        assertTrue(
            results.getArrayElement(i).toString(),
            "PASS".equals(results.getArrayElement(i).getHashValue("status").asString()));
      }
      var metadata =
          context.eval(
              HaraLanguage.ID,
              "(let [metadata (meta (var polis.common.emit-helper/emit-typed-args))]"
                  + " [(:doc metadata) (:added metadata) (count (:arglists metadata))])");
      assertEquals(
          "create types args from declarationns",
          metadata.getArrayElement(0).asString());
      assertEquals("3.0", metadata.getArrayElement(1).asString());
      assertEquals(2, metadata.getArrayElement(2).asLong());
    }
  }
}
