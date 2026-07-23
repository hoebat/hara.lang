package hara.truffle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.Test;

public class HaraTestIntegrationTest {
  @Test
  public void requiresJavaBackedNamespaceWithQuotedSymbol() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      context.eval(HaraLanguage.ID, "(require 'code.test)");
      Value result = context.eval(HaraLanguage.ID, "(code.test/assert! 1 1)");
      assertTrue(result.asBoolean());
    }
  }

  @Test
  public void registersAndRunsAJavaBackedFactMacro() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      context.eval(HaraLanguage.ID, "(fact \"addition\" (+ 1 2) => 3)");
      Value results = context.eval(HaraLanguage.ID, "(code.test/run!)");
      assertTrue(results.hasArrayElements());
      assertEquals(1, results.getArraySize());
      Value result = results.getArrayElement(0);
      assertTrue(result.hasHashEntries());
      assertEquals("PASS", result.getHashValue("status").asString());
    }
  }
}
