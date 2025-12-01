package hara.kernel.base;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Tests for numeric operations in {@link Builtin.Ops}. These tests target previously uncovered
 * arithmetic and comparison methods.
 */
public class BuiltinOpsTest {

  @Test
  public void testAdd() {
    Number result = Builtin.Ops.add(10, 5);
    assertEquals(java.math.BigInteger.valueOf(15L), result);
  }

  @Test
  public void testMinus() {
    Number result = Builtin.Ops.minus(10, 5);
    assertEquals(java.math.BigInteger.valueOf(5L), result);
  }

  @Test
  public void testMultiply() {
    Number result = Builtin.Ops.multiply(10, 5);
    assertEquals(java.math.BigInteger.valueOf(50L), result);
  }

  @Test
  public void testDivide() {
    Number result = Builtin.Ops.divide(10, 5);
    assertEquals(java.math.BigInteger.valueOf(2L), result);
  }

  @Test
  public void testInc() {
    Number result = Builtin.Ops.inc(5);
    assertEquals(java.math.BigInteger.valueOf(6L), result);
  }

  @Test
  public void testDec() {
    Number result = Builtin.Ops.dec(5);
    assertEquals(java.math.BigInteger.valueOf(4L), result);
  }

  @Test
  public void testLt() {
    assertTrue(Builtin.Ops.lt(5, 10));
    assertFalse(Builtin.Ops.lt(10, 5));
    assertFalse(Builtin.Ops.lt(5, 5));
  }

  @Test
  public void testGt() {
    assertTrue(Builtin.Ops.gt(10, 5));
    assertFalse(Builtin.Ops.gt(5, 10));
    assertFalse(Builtin.Ops.gt(5, 5));
  }

  @Test
  public void testLte() {
    assertTrue(Builtin.Ops.lte(5, 10));
    assertTrue(Builtin.Ops.lte(5, 5));
    assertFalse(Builtin.Ops.lte(10, 5));
  }

  @Test
  public void testGte() {
    assertTrue(Builtin.Ops.gte(10, 5));
    assertTrue(Builtin.Ops.gte(5, 5));
    assertFalse(Builtin.Ops.gte(5, 10));
  }

  @Test
  public void testIsPos() {
    assertTrue(Builtin.Ops.isPos(5));
    assertFalse(Builtin.Ops.isPos(-5));
    assertFalse(Builtin.Ops.isPos(0));
  }

  @Test
  public void testIsNeg() {
    assertTrue(Builtin.Ops.isNeg(-5));
    assertFalse(Builtin.Ops.isNeg(5));
    assertFalse(Builtin.Ops.isNeg(0));
  }

  @Test
  public void testIsZero() {
    assertTrue(Builtin.Ops.isZero(0));
    assertFalse(Builtin.Ops.isZero(5));
    assertFalse(Builtin.Ops.isZero(-5));
  }

  @Test
  public void testIdentical() {
    Object obj = new Object();
    assertTrue(Builtin.Ops.identical(obj, obj));
    assertFalse(Builtin.Ops.identical(new Object(), new Object()));
    assertTrue(Builtin.Ops.identical(null, null));
  }

  @Test
  public void testEquals() {
    assertTrue(Builtin.Ops.equals("hello", "hello"));
    assertFalse(Builtin.Ops.equals("hello", "world"));
    assertTrue(Builtin.Ops.equals(null, null));
  }

  @Test
  public void testEquivalent() {
    assertTrue(Builtin.Ops.equivalent(5, 5));
    assertFalse(Builtin.Ops.equivalent(5, 10));
  }

  @Test
  public void testNotEquivalent() {
    assertTrue(Builtin.Ops.notEquivalent(5, 10));
    assertFalse(Builtin.Ops.notEquivalent(5, 5));
  }

  @Test
  public void testBitAnd() {
    Number result = Builtin.Ops.bitAnd(6, 3); // 110 & 011 = 010
    assertEquals(java.math.BigInteger.valueOf(2L), result);
  }

  @Test
  public void testBitOr() {
    Number result = Builtin.Ops.bitOr(6, 3); // 110 | 011 = 111
    assertEquals(java.math.BigInteger.valueOf(7L), result);
  }
}
