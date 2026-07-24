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
}
