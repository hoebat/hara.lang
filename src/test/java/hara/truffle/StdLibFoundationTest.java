package hara.truffle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.Test;

public class StdLibFoundationTest {
  @Test
  public void javaExportsWinAndHalFillsMissingSymbols() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      assertEquals(
          "Returns a lazy sequence produced by applying function to each input.",
          context.eval(HaraLanguage.ID, "(get (meta #'map) :doc)").asString());
      assertEquals(
          42,
          context
              .eval(HaraLanguage.ID, "((std.lib.foundation/comp2 inc inc) 40)")
              .asLong());
      assertEquals(
          "[2 3 4]",
          context.eval(HaraLanguage.ID, "(vec (map inc [1 2 3]))").toString());
    }
  }

  @Test
  public void fallbackReloadPreservesJavaVarsAndRefreshesHal() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      Value before = context.eval(HaraLanguage.ID, "std.lib.foundation/map");
      long revision =
          context
              .eval(
                  HaraLanguage.ID,
                  "(module-revision \"std/lib/foundation.hal\")")
              .asLong();
      context.eval(
          HaraLanguage.ID,
          "(require 'std.lib.foundation {:reload true})");
      Value after = context.eval(HaraLanguage.ID, "std.lib.foundation/map");
      assertEquals(before.toString(), after.toString());
      assertEquals(
          "Returns a lazy sequence produced by applying function to each input.",
          context
              .eval(
                  HaraLanguage.ID,
                  "(get (meta #'std.lib.foundation/map) :doc)")
              .asString());
      assertEquals(
          revision + 1,
          context
              .eval(
                  HaraLanguage.ID,
                  "(module-revision \"std/lib/foundation.hal\")")
              .asLong());
      assertEquals(
          42,
          context
              .eval(HaraLanguage.ID, "((std.lib.foundation/comp2 inc inc) 40)")
              .asLong());
    }
  }

  @Test
  public void projectNamespacesMayShadowReferredFoundationVars() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      assertEquals(
          "[42 2]",
          context
              .eval(
                  HaraLanguage.ID,
                  "(ns testing.shadow) "
                      + "(def map 42) "
                      + "[map (first (std.lib.foundation/map inc [1]))]")
              .toString());
    }
  }

  @Test
  public void collectionTransformsAreEagerAndPreserveExplicitArrays() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      assertEquals(
          "[true true true true true]",
          context
              .eval(
                  HaraLanguage.ID,
                  "[(seq? (map inc [1 2 3])) "
                      + "(vector? ((map inc) [1 2 3])) "
                      + "(array? ((map inc) (array 1 2 3))) "
                      + "(vector? ((comp (map inc) (map inc)) [1 2 3])) "
                      + "(seq? (partition 2 [1 2 3]))]")
              .toString());
      assertEquals(
          "[[2 3 4] [3 4 5] [[1 2]]]",
          context
              .eval(
                  HaraLanguage.ID,
                  "[((map inc) [1 2 3]) "
                      + "((comp (map inc) (map inc)) [1 2 3]) "
                      + "((partition 2) [1 2 3])]")
              .toString());
      assertEquals(
          "[2, 3, 4]",
          context.eval(HaraLanguage.ID, "((map inc) (array 1 2 3))").toString());
    }
  }

  @Test
  public void optimizedSequenceOperationsMatchTheirHalDefinitions() throws Exception {
    String source;
    try (InputStream input =
        StdLibFoundationTest.class
            .getClassLoader()
            .getResourceAsStream("std/lib/foundation.hal")) {
      assertTrue("missing foundation fallback resource", input != null);
      source =
          new String(input.readAllBytes(), StandardCharsets.UTF_8)
              .replace(
                  "(ns std.lib.foundation)",
                  "(ns testing.foundation-fallback)");
    }
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      context.eval(HaraLanguage.ID, source);
      assertEquals(
          "[[2 3 4] [2 3 4]]",
          context
              .eval(
                  HaraLanguage.ID,
                  "[(vec (std.lib.foundation/map inc [1 2 3])) "
                      + " (vec (testing.foundation-fallback/map inc [1 2 3]))]")
              .toString());
      assertEquals(
          "[10 10]",
          context
              .eval(
                  HaraLanguage.ID,
                  "[(std.lib.foundation/reduce + 0 [1 2 3 4]) "
                      + " (testing.foundation-fallback/reduce + 0 [1 2 3 4])]")
              .toString());
      assertEquals(
          "[[1 2 1 2 1] [1 2 1 2 1]]",
          context
              .eval(
                  HaraLanguage.ID,
                  "[(vec (std.lib.foundation/take 5 "
                      + "       (std.lib.foundation/cycle [1 2]))) "
                      + " (vec (testing.foundation-fallback/take 5 "
                      + "       (testing.foundation-fallback/cycle [1 2])))]")
              .toString());
    }
  }
}
