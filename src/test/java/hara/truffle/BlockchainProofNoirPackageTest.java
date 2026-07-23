package hara.truffle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.junit.Test;

public class BlockchainProofNoirPackageTest {
  private static final String SOURCE = "fn main(value: u8) { assert(value > 0); }";

  @Test
  public void requireDiscoversAndGeneratesThePackagedNamespace() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      PolyglotException missing =
          assertThrows(
              PolyglotException.class,
              () -> context.eval(HaraLanguage.ID, "(blockchain.proof.noir/available?)"));
      assertTrue(missing.getMessage().contains("Unbound symbol"));

      assertEquals(
          SOURCE,
          context
              .eval(
                  HaraLanguage.ID,
                  "(ns app (:require [blockchain.proof.noir])) "
                      + "(blockchain.proof.noir/source "
                      + "(blockchain.proof.noir/program \"identity_demo\" \""
                      + SOURCE
                      + "\"))")
              .asString());
    }
  }

  @Test
  public void packagedNamespaceSupportsAliasAndReferWithoutANoirIntrinsicAlias() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      PolyglotException lowLevelAlias =
          assertThrows(
              PolyglotException.class, () -> context.eval(HaraLanguage.ID, "(noir/available?)"));
      assertTrue(lowLevelAlias.getMessage().contains("Unbound symbol"));
      assertFalse(
          context
              .eval(
                  HaraLanguage.ID,
                  "(ns app (:require [blockchain.proof.noir :as noir :refer [available?]])) "
                      + "(available?)")
              .asBoolean());
      assertEquals(
          SOURCE,
          context
              .eval(
                  HaraLanguage.ID,
                  "(noir/source (noir/program \"identity_demo\" \"" + SOURCE + "\"))")
              .asString());
    }
  }

  @Test
  public void providerFailuresRemainStableThroughThePackageProxy() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      PolyglotException error =
          assertThrows(
              PolyglotException.class,
              () ->
                  context.eval(
                      HaraLanguage.ID,
                      "(ns app (:require [blockchain.proof.noir :as noir])) "
                          + "(deref (noir/compile "
                          + "(noir/program \"identity_demo\" \""
                          + SOURCE
                          + "\")))"));
      assertTrue(error.getMessage().contains("capability-scoped NoirWasmLoader"));
    }
  }
}
