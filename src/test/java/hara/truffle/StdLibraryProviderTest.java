package hara.truffle;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class StdLibraryProviderTest {
  @Test
  public void loadsBlockThroughTheLibraryProvider() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      context.eval(HaraLanguage.ID, "(require 'std.lib.block)");
      assertEquals("abc", context.eval(HaraLanguage.ID, "(std.lib.block/parse \"abc\")").toString());
      assertTrue(
          context
              .eval(HaraLanguage.ID, "(std.lib.block/parse-root \"a b\")")
              .toString()
              .contains("a b"));
      context.eval(HaraLanguage.ID, "(std.lib.block/parse-first \"  a b\")");
      assertEquals(
          "Parses the first source-preserving block.",
          context
              .eval(HaraLanguage.ID, "(get (meta (var std.lib.block/parse)) :doc)")
              .asString());
      assertThrows(
          PolyglotException.class,
          () -> context.eval(HaraLanguage.ID, "(std.lib.block/parse)"));
      assertThrows(
          PolyglotException.class,
          () -> context.eval(HaraLanguage.ID, "(std.lib.block/parse \"a\" \"b\")"));
      assertThrows(
          PolyglotException.class,
          () -> context.eval(HaraLanguage.ID, "(var std.lib.block/grid)"));
      assertThrows(
          PolyglotException.class,
          () -> context.eval(HaraLanguage.ID, "(require 'std.block)"));
    }
  }

  @Test
  public void loadsZipThroughTheLibraryProvider() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      context.eval(HaraLanguage.ID, "(require 'std.lib.zip)");
      context.eval(
          HaraLanguage.ID,
          "(std.lib.zip/step-inside (std.lib.zip/zipper [1 2]))");
      context.eval(
          HaraLanguage.ID,
          "(-> [1 [2]] std.lib.zip/zipper std.lib.zip/step-inside "
              + "std.lib.zip/step-right std.lib.zip/step-inside "
              + "std.lib.zip/step-outside std.lib.zip/step-left)");
      assertThrows(
          PolyglotException.class,
          () -> context.eval(HaraLanguage.ID, "(std.lib.zip/step-left 1)"));
      assertThrows(
          PolyglotException.class,
          () -> context.eval(HaraLanguage.ID, "(std.lib.zip/zipper)"));
      assertThrows(
          PolyglotException.class,
          () -> context.eval(HaraLanguage.ID, "(std.lib.zip/step-right)"));
      assertThrows(
          PolyglotException.class,
          () -> context.eval(HaraLanguage.ID, "(var std.lib.zip/remove)"));
    }
  }

  @Test
  public void requireAliasesResolveToProviderBackedNamespaces() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      context.eval(
          HaraLanguage.ID,
          "(ns provider.aliases "
              + "(:require [std.lib.block :as block] [std.lib.zip :as zip]))");
      assertEquals("abc", context.eval(HaraLanguage.ID, "(block/parse \"abc\")").toString());
      assertTrue(context.eval(HaraLanguage.ID, "(zip/zipper [1])").toString().startsWith("#zip"));
    }
  }

  @Test
  public void providersDeclareCanonicalNamespacesAndStableOrder() {
    HaraLibraryProvider block = new StdBlockLibraryProvider();
    HaraLibraryProvider zip = new StdZipLibraryProvider();
    assertEquals("std.lib.block", block.namespace());
    assertEquals("std.lib.zip", zip.namespace());
    assertEquals(20, block.order());
    assertEquals(20, zip.order());
  }
}
