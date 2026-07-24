package hara.truffle;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.graalvm.polyglot.Context;
import org.junit.Test;

public class StdLibCoroutineTest {
  @Test
  public void createMakesSuspendedCoroutine() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      context.eval(HaraLanguage.ID, "(require 'std.lib.coroutine)");
      assertTrue(
          context
              .eval(
                  HaraLanguage.ID,
                  "(std.lib.coroutine/coroutine? (std.lib.coroutine/create (fn [x] x)))")
              .asBoolean());
      assertFalse(
          context.eval(HaraLanguage.ID, "(std.lib.coroutine/coroutine? 42)").asBoolean());
      assertTrue(
          context
              .eval(
                  HaraLanguage.ID,
                  "(= :suspended (std.lib.coroutine/status"
                      + " (std.lib.coroutine/create (fn [x] x))))")
              .asBoolean());
    }
  }
}
