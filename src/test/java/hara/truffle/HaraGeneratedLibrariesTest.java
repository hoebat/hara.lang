package hara.truffle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.junit.Test;

public class HaraGeneratedLibrariesTest {
  @Test
  public void generatedLibrariesHaveDefaultAliasesWithoutLoadingFiles() {
    try (Context context = context()) {
      assertEquals("hara", context.eval(HaraLanguage.ID, "(str/trim \"  hara  \")").asString());
      assertEquals(255, context.eval(HaraLanguage.ID, "(bytes/get (bytes -1) 0)").asLong());
      assertTrue(
          context
              .eval(
                  HaraLanguage.ID,
                  "(promise/native? (promise/new (fn [resolve reject] (resolve 1))))")
              .asBoolean());
    }
  }

  @Test
  public void intrinsicsAllExplicitlyKeepsEveryDefaultAlias() {
    try (Context context = context()) {
      assertEquals(
          1,
          context
              .eval(HaraLanguage.ID, "(ns app (:intrinsics :all)) (bytes/count (str/encode \"x\"))")
              .asLong());
    }
  }

  @Test
  public void promisesRunAndAdoptCallbackPromises() {
    try (Context context = context()) {
      assertEquals(
          42,
          context
              .eval(
                  HaraLanguage.ID,
                  "(deref (promise/then (promise/run (fn [] 40)) "
                      + "(fn [x] (promise/run (fn [] (+ x 2))))))")
              .asLong());
      assertEquals(
          7,
          context
              .eval(
                  HaraLanguage.ID,
                  "(deref (promise/catch "
                      + "(promise/run (fn [] (throw \"bad\"))) (fn [error] 7)))")
              .asLong());
      assertEquals(
          3,
          context
              .eval(
                  HaraLanguage.ID,
                  "(. (deref (promise/all "
                      + "[(promise/run (fn [] 1)) (promise/run (fn [] 3))])) (get 1))")
              .asLong());
      assertEquals(
          9,
          context
              .eval(HaraLanguage.ID, "(deref (promise/run (fn [] (promise/run (fn [] 9)))))")
              .asLong());
      assertEquals(
          4,
          context
              .eval(
                  HaraLanguage.ID,
                  "(deref (promise/finally (promise/run (fn [] 4)) "
                      + "(fn [] (promise/run (fn [] 99)))))")
              .asLong());
    }
  }

  @Test
  public void intrinsicsCanExcludeAndRenameGeneratedAliases() {
    try (Context context = context()) {
      assertEquals(
          "HARA",
          context
              .eval(
                  HaraLanguage.ID,
                  "(ns app (:intrinsics {:exclude [bytes] :aliases {string text}})) "
                      + "(text/to-upper \"hara\")")
              .asString());
      PolyglotException missing =
          assertThrows(
              PolyglotException.class,
              () -> context.eval(HaraLanguage.ID, "(bytes/count (bytes 1))"));
      assertTrue(missing.getMessage().contains("Unbound symbol: bytes/count"));
      assertEquals("x", context.eval(HaraLanguage.ID, "(str \"x\")").asString());
    }
  }

  @Test
  public void generatedLibrariesAlsoSupportRequireAsAndRefer() {
    try (Context context = context()) {
      assertEquals(
          "x",
          context
              .eval(
                  HaraLanguage.ID,
                  "(ns app (:intrinsics {:exclude [string]}) "
                      + "(:require [hara.lib.string :as text :refer [trim]])) "
                      + "(trim (text/trim \" x \"))")
              .asString());
    }
  }

  @Test
  public void dotCallsAreRestrictedToMarkedArraysAndObjects() {
    try (Context context = context()) {
      assertEquals(
          6,
          context
              .eval(HaraLanguage.ID, "(. (array 1 2 3) (fold-left (fn [out x] (+ out x)) 0))")
              .asLong());
      assertEquals(
          3,
          context
              .eval(
                  HaraLanguage.ID,
                  "(let [a (array 1 2 3 4)] (. (. a (filter (fn [x] (> x 2)))) (get 0)))")
              .asLong());
      assertEquals(
          42,
          context
              .eval(
                  HaraLanguage.ID,
                  "(let [o (object \"answer\" 41)] (. o (set \"answer\" 42)) "
                      + "(. o (get \"answer\")))")
              .asLong());
      PolyglotException denied =
          assertThrows(
              PolyglotException.class, () -> context.eval(HaraLanguage.ID, "(. [1 2] (get 0))"));
      assertTrue(
          denied.getMessage().contains("only supported on values created by array or object"));
    }
  }

  @Test
  public void bitOperationsUseSignedThirtyTwoBitSemantics() {
    try (Context context = context()) {
      assertEquals(2, context.eval(HaraLanguage.ID, "(bit-and 6 3)").asLong());
      assertEquals(-1, context.eval(HaraLanguage.ID, "(bit-not 0)").asLong());
      assertEquals(-2, context.eval(HaraLanguage.ID, "(bit-shift-right -4 1)").asLong());
    }
  }

  private static Context context() {
    return Context.newBuilder(HaraLanguage.ID).build();
  }
}
