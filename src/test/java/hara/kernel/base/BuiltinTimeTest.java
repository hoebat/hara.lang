package hara.kernel.base;

import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Tests for time-related utility methods in {@link Builtin.Time}. These tests target previously
 * uncovered methods.
 */
public class BuiltinTimeTest {

  @Test
  public void testNowReturnsCurrentTime() {
    long before = System.currentTimeMillis();
    long now = Builtin.Time.now();
    long after = System.currentTimeMillis();

    assertTrue("now() should return current time", now >= before && now <= after);
  }

  @Test
  public void testBenchMeasuresExecutionTime() {
    long duration =
        Builtin.Time.bench(
            new hara.lang.protocol.IFn() {
              @Override
              public Object invoke(Object... args) {
                try {
                  Thread.sleep(50);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                return null;
              }
            });

    // Should take at least 50ms
    assertTrue("bench() should measure execution time accurately", duration >= 50);
  }
}
