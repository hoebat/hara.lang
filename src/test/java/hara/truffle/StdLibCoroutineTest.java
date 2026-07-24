package hara.truffle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
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

  @Test
  public void resumeRunsBodyToCompletion() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      context.eval(HaraLanguage.ID, "(require 'std.lib.coroutine)");
      context.eval(
          HaraLanguage.ID, "(def c-resume (std.lib.coroutine/create (fn [x] (* x 2))))");
      assertEquals(
          42, context.eval(HaraLanguage.ID, "(std.lib.coroutine/resume c-resume 21)").asLong());
      assertTrue(
          context
              .eval(HaraLanguage.ID, "(= :dead (std.lib.coroutine/status c-resume))")
              .asBoolean());
    }
  }

  @Test
  public void resumeOnDeadThrows() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      context.eval(HaraLanguage.ID, "(require 'std.lib.coroutine)");
      context.eval(HaraLanguage.ID, "(def c-dead (std.lib.coroutine/create (fn [] 1)))");
      context.eval(HaraLanguage.ID, "(std.lib.coroutine/resume c-dead)");
      PolyglotException error =
          assertThrows(
              PolyglotException.class,
              () -> context.eval(HaraLanguage.ID, "(std.lib.coroutine/resume c-dead)"));
      assertTrue(error.getMessage().contains("dead"));
    }
  }

  @Test
  public void bodyErrorRethrowsAtResumeAndKillsCoroutine() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      context.eval(HaraLanguage.ID, "(require 'std.lib.coroutine)");
      context.eval(HaraLanguage.ID, "(def c-err (std.lib.coroutine/create (fn [] (/ 1 0))))");
      assertThrows(
          PolyglotException.class,
          () -> context.eval(HaraLanguage.ID, "(std.lib.coroutine/resume c-err)"));
      assertTrue(
          context
              .eval(HaraLanguage.ID, "(= :dead (std.lib.coroutine/status c-err))")
              .asBoolean());
    }
  }
}
