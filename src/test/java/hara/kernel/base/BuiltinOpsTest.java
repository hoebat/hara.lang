package hara.kernel.base;

import org.junit.Test;
import static org.junit.Assert.*;

import static org.junit.Assert.*;

/**
 * Tests for numeric operations in {@link Builtin.Ops}. These tests target previously uncovered
 * arithmetic and comparison methods.
 */
public class BuiltinOpsTest {

  @Test
  public void testMinMax() {
    assertEquals(1L, Builtin.Ops.min(1L, 2L));
    assertEquals(2L, Builtin.Ops.max(1L, 2L));
    assertEquals(1.0, Builtin.Ops.min(1.0, 2.0));
    assertEquals(2.0, Builtin.Ops.max(1.0, 2.0));
  }

  @Test
  public void testRounding() {
    assertEquals(1.0, ((Number) Builtin.Ops.floor(1.5)).doubleValue(), 0.001);
    assertEquals(2.0, ((Number) Builtin.Ops.ceil(1.5)).doubleValue(), 0.001);
    assertEquals(2L, ((Number) Builtin.Ops.round(1.5)).longValue());
    assertEquals(1L, ((Number) Builtin.Ops.round(1.4)).longValue());
  }

  @Test
  public void testAbs() {
    assertEquals(1.0, ((Number) Builtin.Ops.abs(-1.0)).doubleValue(), 0.001);
    assertEquals(1.0, ((Number) Builtin.Ops.abs(1.0)).doubleValue(), 0.001);
    assertEquals(1L, ((Number) Builtin.Ops.abs(-1L)).longValue());
    assertEquals(1L, ((Number) Builtin.Ops.abs(1L)).longValue());
  }

  @Test
  public void testQuotRemMod() {
    assertEquals(3L, ((Number) Builtin.Ops.quot(10L, 3L)).longValue());
    assertEquals(1L, ((Number) Builtin.Ops.rem(10L, 3L)).longValue());
    assertEquals(1L, ((Number) Builtin.Ops.mod(10L, 3L)).longValue());
  }

  @Test
  public void testIncDecDouble() {
    assertEquals(2.0, ((Number) Builtin.Ops.inc(1.0)).doubleValue(), 0.001);
    assertEquals(0.0, ((Number) Builtin.Ops.dec(1.0)).doubleValue(), 0.001);
  }
}
