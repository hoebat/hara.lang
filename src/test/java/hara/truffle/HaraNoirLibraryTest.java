package hara.truffle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.junit.Test;

public class HaraNoirLibraryTest {
  private static final String SOURCE = "fn main(value: u8) { assert(value > 0); }";

  @Test
  public void constructsStableProgramsThroughTheGeneratedNamespace() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      assertTrue(
          context
              .eval(
                  HaraLanguage.ID,
                  "(ns app (:require [blockchain.proof.noir :as noir])) "
                      + "(let [a (noir/program \"first_cut\" \""
                      + SOURCE
                      + "\") b (noir/program \"first_cut\" \""
                      + SOURCE
                      + "\")] (= (noir/cache-key a) (noir/cache-key b)))")
              .asBoolean());
      assertEquals(
          "[package]\nname = \"first_cut\"\ntype = \"bin\"\n",
          context
              .eval(
                  HaraLanguage.ID,
                  "(ns app (:require [blockchain.proof.noir :as noir])) "
                      + "(noir/manifest (noir/program \"first_cut\" \""
                      + SOURCE
                      + "\"))")
              .asString());
    }
  }

  @Test
  public void unavailableLoaderIsAnExplicitCapabilityFailure() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      context.eval(HaraLanguage.ID, "(ns app (:require [blockchain.proof.noir :as noir]))");
      assertFalse(context.eval(HaraLanguage.ID, "(noir/available?)").asBoolean());
      assertEquals("unavailable", context.eval(HaraLanguage.ID, "(noir/loader-id)").asString());
      PolyglotException error =
          assertThrows(
              PolyglotException.class,
              () ->
                  context.eval(
                      HaraLanguage.ID,
                      "(deref (noir/compile (noir/program \"first_cut\" \"" + SOURCE + "\")))"));
      assertTrue(error.getMessage().contains("capability-scoped NoirWasmLoader"));
      PolyglotException proveError =
          assertThrows(
              PolyglotException.class,
              () -> context.eval(HaraLanguage.ID, "(noir/prove nil \"{}\")"));
      assertTrue(proveError.getMessage().contains("expects a compiled Noir artifact"));
    }
  }
}
