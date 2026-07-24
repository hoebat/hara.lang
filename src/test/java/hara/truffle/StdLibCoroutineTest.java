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

  @Test
  public void yieldExchangesValuesBothWays() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      context.eval(HaraLanguage.ID, "(require 'std.lib.coroutine)");
      context.eval(
          HaraLanguage.ID,
          "(def c-y (std.lib.coroutine/create"
              + " (fn [start]"
              + "   (let [a (std.lib.coroutine/yield (* start start))]"
              + "     (let [b (std.lib.coroutine/yield :second)]"
              + "       [a b])))))");
      assertEquals(
          100, context.eval(HaraLanguage.ID, "(std.lib.coroutine/resume c-y 10)").asLong());
      assertTrue(
          context
              .eval(
                  HaraLanguage.ID,
                  "(= :second (std.lib.coroutine/resume c-y :got-a))")
              .asBoolean());
      assertEquals(
          "[:got-a :got-b]",
          context.eval(HaraLanguage.ID, "(std.lib.coroutine/resume c-y :got-b)").toString());
      assertTrue(
          context.eval(HaraLanguage.ID, "(= :dead (std.lib.coroutine/status c-y))").asBoolean());
    }
  }

  @Test
  public void multiYieldPacksVectorAndZeroYieldsNil() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      context.eval(HaraLanguage.ID, "(require 'std.lib.coroutine)");
      context.eval(
          HaraLanguage.ID,
          "(def c-m (std.lib.coroutine/create"
              + " (fn [] (std.lib.coroutine/yield 1 2 3) (std.lib.coroutine/yield))))");
      assertEquals(
          "[1 2 3]",
          context.eval(HaraLanguage.ID, "(std.lib.coroutine/resume c-m)").toString());
      assertTrue(context.eval(HaraLanguage.ID, "(std.lib.coroutine/resume c-m 9 8)").isNull());
      assertTrue(
          context
              .eval(HaraLanguage.ID, "(= :suspended (std.lib.coroutine/status c-m))")
              .asBoolean());
      context.eval(HaraLanguage.ID, "(std.lib.coroutine/resume c-m)");
      assertTrue(
          context.eval(HaraLanguage.ID, "(= :dead (std.lib.coroutine/status c-m))").asBoolean());
    }
  }

  @Test
  public void yieldWorksFromNestedHelper() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      context.eval(HaraLanguage.ID, "(require 'std.lib.coroutine)");
      context.eval(
          HaraLanguage.ID, "(defn helper-n [x] (std.lib.coroutine/yield (* x 10)))");
      context.eval(
          HaraLanguage.ID,
          "(def c-n (std.lib.coroutine/create (fn [] (helper-n 3) :end)))");
      assertEquals(30, context.eval(HaraLanguage.ID, "(std.lib.coroutine/resume c-n)").asLong());
      assertTrue(
          context
              .eval(HaraLanguage.ID, "(= :end (std.lib.coroutine/resume c-n))")
              .asBoolean());
    }
  }

  @Test
  public void yieldOutsideCoroutineThrows() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      context.eval(HaraLanguage.ID, "(require 'std.lib.coroutine)");
      PolyglotException error =
          assertThrows(
              PolyglotException.class,
              () -> context.eval(HaraLanguage.ID, "(std.lib.coroutine/yield 1)"));
      assertTrue(error.getMessage().contains("outside"));
    }
  }

  @Test
  public void reentrantResumeThrows() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      context.eval(HaraLanguage.ID, "(require 'std.lib.coroutine)");
      context.eval(
          HaraLanguage.ID,
          "(def c-r (std.lib.coroutine/create (fn [] (std.lib.coroutine/resume c-r))))");
      PolyglotException error =
          assertThrows(
              PolyglotException.class,
              () -> context.eval(HaraLanguage.ID, "(std.lib.coroutine/resume c-r)"));
      assertTrue(error.getMessage().contains("running"));
    }
  }

  @Test
  public void nestedCoroutinesResumeEachOther() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      context.eval(HaraLanguage.ID, "(require 'std.lib.coroutine)");
      context.eval(
          HaraLanguage.ID,
          "(def c-inner (std.lib.coroutine/create"
              + " (fn [] (std.lib.coroutine/yield :inner-yield) :inner-end)))");
      context.eval(
          HaraLanguage.ID,
          "(def c-outer (std.lib.coroutine/create"
              + " (fn []"
              + "   (std.lib.coroutine/yield (std.lib.coroutine/resume c-inner))"
              + "   (std.lib.coroutine/yield (std.lib.coroutine/resume c-inner :x))"
              + "   :outer-end)))");
      assertTrue(
          context
              .eval(
                  HaraLanguage.ID,
                  "(= :inner-yield (std.lib.coroutine/resume c-outer))")
              .asBoolean());
      assertTrue(
          context
              .eval(
                  HaraLanguage.ID,
                  "(= :inner-end (std.lib.coroutine/resume c-outer))")
              .asBoolean());
      assertTrue(
          context
              .eval(
                  HaraLanguage.ID,
                  "(= :outer-end (std.lib.coroutine/resume c-outer))")
              .asBoolean());
      assertTrue(
          context
              .eval(HaraLanguage.ID, "(= :dead (std.lib.coroutine/status c-outer))")
              .asBoolean());
    }
  }

  @Test
  public void generatorPipelineProducesLazily() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      context.eval(HaraLanguage.ID, "(require 'std.lib.coroutine)");
      context.eval(
          HaraLanguage.ID,
          "(def c-gen (std.lib.coroutine/create"
              + " (fn [n] (loop [i 0]"
              + "   (if (< i n)"
              + "     (do (std.lib.coroutine/yield (* i i)) (recur (inc i)))"
              + "     :done)))))");
      assertEquals(
          "[0 1 4]",
          context
              .eval(
                  HaraLanguage.ID,
                  "[(std.lib.coroutine/resume c-gen 3)"
                      + " (std.lib.coroutine/resume c-gen)"
                      + " (std.lib.coroutine/resume c-gen)]")
              .toString());
      assertTrue(
          context
              .eval(HaraLanguage.ID, "(= :done (std.lib.coroutine/resume c-gen))")
              .asBoolean());
      assertTrue(
          context
              .eval(HaraLanguage.ID, "(= :dead (std.lib.coroutine/status c-gen))")
              .asBoolean());
    }
  }

  @Test
  public void closeRunsFinallyAndKillsCoroutine() throws InterruptedException {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      context.eval(HaraLanguage.ID, "(require 'std.lib.coroutine)");
      context.eval(HaraLanguage.ID, "(def close-log (atom :init))");
      context.eval(
          HaraLanguage.ID,
          "(def c-close (std.lib.coroutine/create"
              + " (fn [] (try (std.lib.coroutine/yield :parked)"
              + "             (finally (reset! close-log :ran))))))");
      assertTrue(
          context
              .eval(
                  HaraLanguage.ID,
                  "(= :parked (std.lib.coroutine/resume c-close))")
              .asBoolean());
      context.eval(HaraLanguage.ID, "(std.lib.coroutine/close c-close)");
      // Wait for the coroutine thread to unwind (close is asynchronous with the interrupt).
      long deadline = System.currentTimeMillis() + 5000;
      while (System.currentTimeMillis() < deadline) {
        if (context
            .eval(HaraLanguage.ID, "(= :ran (deref close-log))")
            .asBoolean()) {
          break;
        }
        Thread.sleep(20);
      }
      assertTrue(
          context.eval(HaraLanguage.ID, "(= :ran (deref close-log))").asBoolean());
      assertTrue(
          context
              .eval(HaraLanguage.ID, "(= :dead (std.lib.coroutine/status c-close))")
              .asBoolean());
      PolyglotException error =
          assertThrows(
              PolyglotException.class,
              () -> context.eval(HaraLanguage.ID, "(std.lib.coroutine/resume c-close)"));
      assertTrue(error.getMessage().contains("dead"));
    }
  }

  @Test
  public void closeOnDeadIsNoOpAndCloseOnRunningThrows() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      context.eval(HaraLanguage.ID, "(require 'std.lib.coroutine)");
      context.eval(HaraLanguage.ID, "(def c-done (std.lib.coroutine/create (fn [] 1)))");
      context.eval(HaraLanguage.ID, "(std.lib.coroutine/resume c-done)");
      assertTrue(
          context
              .eval(
                  HaraLanguage.ID,
                  "(std.lib.coroutine/coroutine? (std.lib.coroutine/close c-done))")
              .asBoolean());
      context.eval(
          HaraLanguage.ID,
          "(def c-self (std.lib.coroutine/create (fn [] (std.lib.coroutine/close c-self))))");
      PolyglotException error =
          assertThrows(
              PolyglotException.class,
              () -> context.eval(HaraLanguage.ID, "(std.lib.coroutine/resume c-self)"));
      assertTrue(error.getMessage().contains("running"));
    }
  }

  @Test
  public void awaitReturnsSettledPromiseValue() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      context.eval(HaraLanguage.ID, "(require 'std.lib.coroutine)");
      context.eval(
          HaraLanguage.ID,
          "(def c-await (std.lib.coroutine/create"
              + " (fn [] (std.lib.coroutine/await (promise/delay 50 (fn [] :delayed-value))))))");
      assertTrue(
          context
              .eval(
                  HaraLanguage.ID,
                  "(= :delayed-value (std.lib.coroutine/resume c-await))")
              .asBoolean());
      assertTrue(
          context
              .eval(HaraLanguage.ID, "(= :dead (std.lib.coroutine/status c-await))")
              .asBoolean());
      // Already-settled promise resolves without parking.
      context.eval(
          HaraLanguage.ID,
          "(def c-quick (std.lib.coroutine/create"
              + " (fn [] (std.lib.coroutine/await (promise/run (fn [] 7))))))");
      assertEquals(
          7, context.eval(HaraLanguage.ID, "(std.lib.coroutine/resume c-quick)").asLong());
    }
  }

  @Test
  public void awaitRethrowsPromiseRejection() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      context.eval(HaraLanguage.ID, "(require 'std.lib.coroutine)");
      context.eval(
          HaraLanguage.ID,
          "(def c-reject (std.lib.coroutine/create"
              + " (fn [] (std.lib.coroutine/await (promise/run (fn [] (/ 1 0)))))))");
      PolyglotException error =
          assertThrows(
              PolyglotException.class,
              () -> context.eval(HaraLanguage.ID, "(std.lib.coroutine/resume c-reject)"));
      assertTrue(error.getMessage().contains("Promise rejected"));
      assertTrue(
          context
              .eval(HaraLanguage.ID, "(= :dead (std.lib.coroutine/status c-reject))")
              .asBoolean());
    }
  }

  @Test
  public void awaitRejectsNonDerefable() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      context.eval(HaraLanguage.ID, "(require 'std.lib.coroutine)");
      PolyglotException error =
          assertThrows(
              PolyglotException.class,
              () -> context.eval(HaraLanguage.ID, "(std.lib.coroutine/await 42)"));
      assertTrue(error.getMessage().contains("derefable"));
    }
  }
}
