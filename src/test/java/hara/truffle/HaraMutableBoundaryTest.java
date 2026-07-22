package hara.truffle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.junit.Test;

public class HaraMutableBoundaryTest {
  @Test
  public void bytesHaveReadableContentEqualityHashCopyAndSliceSemantics() {
    try (Context context = context()) {
      Value bytes = context.eval(HaraLanguage.ID, "(bytes 1 2 -3)");
      assertEquals("(bytes 1 2 -3)", bytes.toString());
      assertTrue(context.eval(HaraLanguage.ID, "(= (bytes 1 2 -3) (bytes 1 2 -3))").asBoolean());
      assertTrue(
          context
              .eval(
                  HaraLanguage.ID,
                  "(= (protocol-call IHash hash (bytes 1 2 -3)) "
                      + "(protocol-call IHash hash (bytes 1 2 -3)))")
              .asBoolean());
      assertEquals(
          2, context.eval(HaraLanguage.ID, "(x:get (byte-slice (bytes 1 2 -3) 1 3) 0)").asLong());
      assertEquals(
          1,
          context
              .eval(
                  HaraLanguage.ID,
                  "(let [source (bytes 1 2)] "
                      + "(let [copy (byte-copy source)] (x:set copy 0 9) (x:get source 0)))")
              .asLong());
    }
  }

  @Test
  public void mutableObjectsUseKeysWhileSequentialTargetsRequireNumericIndexes() {
    try (Context context = context()) {
      assertEquals(
          42,
          context
              .eval(
                  HaraLanguage.ID,
                  "(let [object (x:object)] " + "(x:set object :answer 42) (x:get object :answer))")
              .asLong());
      assertEquals(7, context.eval(HaraLanguage.ID, "(x:get (x:array 1) 9 7)").asLong());

      PolyglotException invalidIndex =
          assertThrows(
              PolyglotException.class,
              () -> context.eval(HaraLanguage.ID, "(x:get (x:array 1) :bad 7)"));
      assertTrue(invalidIndex.getMessage().contains("index must be numeric"));
    }
  }

  @Test
  public void byteOperationsRejectWrongTypesAndInvalidRanges() {
    try (Context context = context()) {
      assertTrue(
          assertThrows(
                  PolyglotException.class, () -> context.eval(HaraLanguage.ID, "(byte-copy [1 2])"))
              .getMessage()
              .contains("byte-copy expects bytes"));
      assertTrue(
          assertThrows(
                  PolyglotException.class,
                  () -> context.eval(HaraLanguage.ID, "(byte-slice (bytes 1 2) 0 3)"))
              .getMessage()
              .contains("range is out of bounds"));
    }
  }

  @Test
  public void mutableMutationBoundsHaveStableDiagnostics() {
    try (Context context = context()) {
      assertTrue(
          assertThrows(
                  PolyglotException.class,
                  () -> context.eval(HaraLanguage.ID, "(x:set (x:array 1) 4 9)"))
              .getMessage()
              .contains("x:set index out of bounds"));
      assertTrue(
          assertThrows(
                  PolyglotException.class,
                  () -> context.eval(HaraLanguage.ID, "(x:delete (x:array 1) 4)"))
              .getMessage()
              .contains("x:delete index out of bounds"));
      assertTrue(
          assertThrows(
                  PolyglotException.class,
                  () -> context.eval(HaraLanguage.ID, "(x:remove (x:array 1) 4)"))
              .getMessage()
              .contains("x:remove index out of bounds"));
    }
  }

  @Test
  public void iteratorFormsAreLazyAndExplicitlyClosable() {
    try (Context context = context()) {
      assertEquals(
          1, context.eval(HaraLanguage.ID, "(let [it (iter [1 2])] (iter-next it))").asLong());
      assertEquals(
          2,
          context
              .eval(HaraLanguage.ID, "(let [it (iter [1 2])] (iter-next it) (iter-next it))")
              .asLong());
      assertTrue(
          !context
              .eval(
                  HaraLanguage.ID,
                  "(let [it (iter [1 2])] (iter-next it) (iter-next it) (iter-has? it))")
              .asBoolean());
      assertTrue(
          assertThrows(
                  PolyglotException.class,
                  () ->
                      context.eval(HaraLanguage.ID, "(iter-next (iter [1])) (iter-next (iter []))"))
              .getMessage()
              .contains("reached the end"));
      context.eval(HaraLanguage.ID, "(iter-close (iter \"abc\"))");
    }
  }

  @Test
  public void concatIsLazyAndIteratorBacked() {
    try (Context context = context()) {
      assertEquals(
          2,
          context
              .eval(
                  HaraLanguage.ID, "(let [it (concat [1 2] [3 4])] (iter-next it) (iter-next it))")
              .asLong());
      assertEquals(
          1, context.eval(HaraLanguage.ID, "(let [it (concat [1] 1)] (iter-next it))").asLong());
      assertThrows(
          PolyglotException.class,
          () ->
              context.eval(
                  HaraLanguage.ID, "(let [it (concat [1] 1)] (iter-next it) (iter-next it))"));
    }
  }

  @Test
  public void iteratorCombinatorsRemainLazyAndUseHaraFunctions() {
    try (Context context = context()) {
      assertEquals(
          4,
          context
              .eval(
                  HaraLanguage.ID,
                  "(let [it (iter-map (fn [x] (* x 2)) [1 2])] (iter-next it) (iter-next it))")
              .asLong());
      assertEquals(
          2,
          context
              .eval(
                  HaraLanguage.ID,
                  "(let [it (iter-filter (fn [x] (= x 2)) [1 2 3])] (iter-next it))")
              .asLong());
      assertEquals(
          2,
          context
              .eval(
                  HaraLanguage.ID,
                  "(let [it (iter-drop 1 (iter-take 3 [1 2 3 4]))] (iter-next it))")
              .asLong());
      assertEquals(
          3,
          context.eval(HaraLanguage.ID, "(x:get (iter-next (iter-zip [1 2] [3 4])) 1)").asLong());
    }
  }

  private static Context context() {
    return Context.newBuilder(HaraLanguage.ID).build();
  }
}
