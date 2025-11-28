package hara.lang.base;

import java.math.BigInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class PTest {

  @Test
  public void testBitOffset() {
    assertEquals(0, P.Bits.bitOffset(1L));
    assertEquals(1, P.Bits.bitOffset(2L));
    assertEquals(2, P.Bits.bitOffset(4L));
    assertEquals(3, P.Bits.bitOffset(8L));
    assertEquals(10, P.Bits.bitOffset(1024L));
    assertEquals(63, P.Bits.bitOffset(1L << 63));
  }

  @Test
  public void testLowestBit() {
    assertEquals(1L, P.Bits.lowestBit(1L));
    assertEquals(2L, P.Bits.lowestBit(2L));
    assertEquals(2L, P.Bits.lowestBit(6L));
    assertEquals(8L, P.Bits.lowestBit(24L));
    assertEquals(1, P.Bits.lowestBit(3));
    assertEquals(4, P.Bits.lowestBit(12));
  }

  @Test
  public void testHighestBit() {
    assertEquals(1L, P.Bits.highestBit(1L));
    assertEquals(2L, P.Bits.highestBit(2L));
    assertEquals(4L, P.Bits.highestBit(6L));
    assertEquals(16L, P.Bits.highestBit(24L));
    assertEquals(2, P.Bits.highestBit(3));
    assertEquals(8, P.Bits.highestBit(12));
  }

  @Test
  public void testLog2Floor() {
    assertEquals(0, P.Bits.log2Floor(1L));
    assertEquals(1, P.Bits.log2Floor(2L));
    assertEquals(2, P.Bits.log2Floor(4L));
    assertEquals(2, P.Bits.log2Floor(5L));
    assertEquals(3, P.Bits.log2Floor(8L));
    assertEquals(3, P.Bits.log2Floor(15L));
    assertEquals(10, P.Bits.log2Floor(1024L));
  }

  @Test
  public void testLog2Ceil() {
    assertEquals(0, P.Bits.log2Ceil(1L));
    assertEquals(1, P.Bits.log2Ceil(2L));
    assertEquals(2, P.Bits.log2Ceil(4L));
    assertEquals(3, P.Bits.log2Ceil(5L));
    assertEquals(3, P.Bits.log2Ceil(8L));
    assertEquals(4, P.Bits.log2Ceil(15L));
    assertEquals(10, P.Bits.log2Ceil(1024L));
  }

  @Test
  public void testMaskBelow() {
    assertEquals(0L, P.Bits.maskBelow(0));
    assertEquals(1L, P.Bits.maskBelow(1));
    assertEquals(3L, P.Bits.maskBelow(2));
    assertEquals(7L, P.Bits.maskBelow(3));
    assertEquals(Long.MAX_VALUE, P.Bits.maskBelow(63));
  }

  @Test
  public void testMaskAbove() {
    assertEquals(-1L, P.Bits.maskAbove(0));
    assertEquals(-2L, P.Bits.maskAbove(1));
    assertEquals(-4L, P.Bits.maskAbove(2));
    assertEquals(-8L, P.Bits.maskAbove(3));
  }

  @Test
  public void testBranchingBit() {
    assertEquals(-1, P.Bits.branchingBit(5L, 5L));
    assertEquals(0, P.Bits.branchingBit(5L, 4L));
    assertEquals(2, P.Bits.branchingBit(5L, 1L));
    assertEquals(3, P.Bits.branchingBit(0L, 8L));
  }

  @Test
  public void testIsPowerOfTwo() {
    assertTrue(P.Bits.isPowerOfTwo(1L));
    assertTrue(P.Bits.isPowerOfTwo(2L));
    assertTrue(P.Bits.isPowerOfTwo(4L));
    assertTrue(P.Bits.isPowerOfTwo(1024L));
    assertFalse(P.Bits.isPowerOfTwo(3L));
    assertFalse(P.Bits.isPowerOfTwo(6L));
    assertFalse(P.Bits.isPowerOfTwo(0L));
  }

  @Test
  public void testBox() {
    assertEquals(true, P.Box.box(true));
    assertEquals(false, P.Box.box(false));
    assertEquals(Byte.valueOf((byte) 5), P.Box.box((byte) 5));
    assertEquals(Character.valueOf('a'), P.Box.box('a'));
    assertEquals(Double.valueOf(3.14), P.Box.box(3.14));
    assertEquals(Float.valueOf(1.618f), P.Box.box(1.618f));
    assertEquals(Integer.valueOf(100), P.Box.box(100));
    assertEquals(Long.valueOf(12345L), P.Box.box(12345L));
    assertEquals("test", P.Box.box("test"));
    assertEquals(Short.valueOf((short) 25), P.Box.box((short) 25));
  }

  // P.Cast Tests
  @Test
  public void testCharCast() {
    assertEquals('a', P.Cast.charCast('a'));
    assertEquals('b', P.Cast.charCast((byte) 98));
    assertEquals('c', P.Cast.charCast((short) 99));
    assertEquals('d', P.Cast.charCast(100));
    assertEquals('e', P.Cast.charCast(101L));
    assertEquals('f', P.Cast.charCast(102.0f));
    assertEquals('g', P.Cast.charCast(103.0));
    assertEquals('h', P.Cast.charCast(Character.valueOf('h')));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testCharCastOutOfRange() {
    P.Cast.charCast(Character.MAX_VALUE + 1);
  }

  @Test
  public void testUncheckedCharCast() {
    assertEquals(
        (char) (Character.MAX_VALUE + 1), P.Cast.uncheckedCharCast(Character.MAX_VALUE + 1));
  }

  @Test
  public void testBooleanCast() {
    assertTrue(P.Cast.booleanCast(true));
    assertFalse(P.Cast.booleanCast(false));
    assertTrue(P.Cast.booleanCast(new Object()));
    assertFalse(P.Cast.booleanCast(null));
  }

  @Test
  public void testByteCast() {
    assertEquals((byte) 10, P.Cast.byteCast((byte) 10));
    assertEquals((byte) 20, P.Cast.byteCast((short) 20));
    assertEquals((byte) 30, P.Cast.byteCast(30));
    assertEquals((byte) 40, P.Cast.byteCast(40L));
    assertEquals((byte) 50, P.Cast.byteCast(50.0f));
    assertEquals((byte) 60, P.Cast.byteCast(60.0));
    assertEquals((byte) 70, P.Cast.byteCast(Byte.valueOf((byte) 70)));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testByteCastOutOfRange() {
    P.Cast.byteCast(Byte.MAX_VALUE + 1);
  }

  @Test
  public void testUncheckedByteCast() {
    assertEquals((byte) (Byte.MAX_VALUE + 1), P.Cast.uncheckedByteCast(Byte.MAX_VALUE + 1));
  }

  @Test
  public void testShortCast() {
    assertEquals((short) 10, P.Cast.shortCast((byte) 10));
    assertEquals((short) 20, P.Cast.shortCast((short) 20));
    assertEquals((short) 30, P.Cast.shortCast(30));
    assertEquals((short) 40, P.Cast.shortCast(40L));
    assertEquals((short) 50, P.Cast.shortCast(50.0f));
    assertEquals((short) 60, P.Cast.shortCast(60.0));
    assertEquals((short) 70, P.Cast.shortCast(Short.valueOf((short) 70)));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testShortCastOutOfRange() {
    P.Cast.shortCast(Short.MAX_VALUE + 1);
  }

  @Test
  public void testUncheckedShortCast() {
    assertEquals((short) (Short.MAX_VALUE + 1), P.Cast.uncheckedShortCast(Short.MAX_VALUE + 1));
  }

  @Test
  public void testIntCast() {
    assertEquals(10, P.Cast.intCast((byte) 10));
    assertEquals(20, P.Cast.intCast((short) 20));
    assertEquals(30, P.Cast.intCast(30));
    assertEquals(40, P.Cast.intCast(40L));
    assertEquals(50, P.Cast.intCast(50.0f));
    assertEquals(60, P.Cast.intCast(60.0));
    assertEquals(70, P.Cast.intCast(Integer.valueOf(70)));
    assertEquals(97, P.Cast.intCast('a'));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testIntCastOutOfRange() {
    P.Cast.intCast(Integer.MAX_VALUE + 1L);
  }

  @Test
  public void testUncheckedIntCast() {
    assertEquals((int) (Integer.MAX_VALUE + 1L), P.Cast.uncheckedIntCast(Integer.MAX_VALUE + 1L));
  }

  @Test
  public void testLongCast() {
    assertEquals(10L, P.Cast.longCast((byte) 10));
    assertEquals(20L, P.Cast.longCast((short) 20));
    assertEquals(30L, P.Cast.longCast(30));
    assertEquals(40L, P.Cast.longCast(40L));
    assertEquals(50L, P.Cast.longCast(50.0f));
    assertEquals(60L, P.Cast.longCast(60.0));
    assertEquals(70L, P.Cast.longCast(Long.valueOf(70)));
  }

  @Test(expected = IllegalArgumentException.class)
  public void testLongCastOutOfRange() {
    P.Cast.longCast(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE));
  }

  @Test
  public void testUncheckedLongCast() {
    assertEquals(
        Long.MIN_VALUE,
        P.Cast.uncheckedLongCast(BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)));
  }

  @Test
  public void testFloatCast() {
    assertEquals(10.0f, P.Cast.floatCast((byte) 10), 0.0f);
    assertEquals(20.0f, P.Cast.floatCast((short) 20), 0.0f);
    assertEquals(30.0f, P.Cast.floatCast(30), 0.0f);
    assertEquals(40.0f, P.Cast.floatCast(40L), 0.0f);
    assertEquals(50.0f, P.Cast.floatCast(50.0f), 0.0f);
    assertEquals(60.0f, P.Cast.floatCast(60.0), 0.0f);
    assertEquals(70.0f, P.Cast.floatCast(Float.valueOf(70.0f)), 0.0f);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testFloatCastOutOfRange() {
    P.Cast.floatCast(Double.MAX_VALUE);
  }

  @Test
  public void testUncheckedFloatCast() {
    assertEquals(Float.POSITIVE_INFINITY, P.Cast.uncheckedFloatCast(Double.MAX_VALUE), 0.0f);
  }

  @Test
  public void testDoubleCast() {
    assertEquals(10.0, P.Cast.doubleCast((byte) 10), 0.0);
    assertEquals(20.0, P.Cast.doubleCast((short) 20), 0.0);
    assertEquals(30.0, P.Cast.doubleCast(30), 0.0);
    assertEquals(40.0, P.Cast.doubleCast(40L), 0.0);
    assertEquals(50.0, P.Cast.doubleCast(50.0f), 0.0);
    assertEquals(60.0, P.Cast.doubleCast(60.0), 0.0);
    assertEquals(70.0, P.Cast.doubleCast(Double.valueOf(70.0)), 0.0);
  }

  // P.Complex Tests
  @Test
  public void testComplexArithmetic() {
    P.Complex a = new P.Complex(5.0, 6.0);
    P.Complex b = new P.Complex(-3.0, 4.0);

    P.Complex sum = a.plus(b);
    assertEquals(2.0, sum.re(), 0.001);
    assertEquals(10.0, sum.im(), 0.001);

    P.Complex difference = a.minus(b);
    assertEquals(8.0, difference.re(), 0.001);
    assertEquals(2.0, difference.im(), 0.001);

    P.Complex product = a.times(b);
    assertEquals(-39.0, product.re(), 0.001);
    assertEquals(2.0, product.im(), 0.001);

    P.Complex quotient = a.divides(b);
    assertEquals(0.36, quotient.re(), 0.001);
    assertEquals(-1.52, quotient.im(), 0.001);

    P.Complex scaled = a.scale(2.0);
    assertEquals(10.0, scaled.re(), 0.001);
    assertEquals(12.0, scaled.im(), 0.001);
  }

  @Test
  public void testComplexProperties() {
    P.Complex a = new P.Complex(3.0, 4.0);
    assertEquals(5.0, a.abs(), 0.001);
    assertEquals(0.927, a.phase(), 0.001);
  }

  @Test
  public void testComplexConjugateReciprocal() {
    P.Complex a = new P.Complex(3.0, 4.0);

    P.Complex conjugate = a.conjugate();
    assertEquals(3.0, conjugate.re(), 0.001);
    assertEquals(-4.0, conjugate.im(), 0.001);

    P.Complex reciprocal = a.reciprocal();
    assertEquals(0.12, reciprocal.re(), 0.001);
    assertEquals(-0.16, reciprocal.im(), 0.001);
  }

  @Test
  public void testComplexTrigonometric() {
    P.Complex a = new P.Complex(0.5, 0.2);

    P.Complex exp = a.exp();
    assertEquals(1.616, exp.re(), 0.001);
    assertEquals(0.327, exp.im(), 0.001);

    P.Complex sin = a.sin();
    assertEquals(0.489, sin.re(), 0.001);
    assertEquals(0.177, sin.im(), 0.001);

    P.Complex cos = a.cos();
    assertEquals(0.895, cos.re(), 0.001);
    assertEquals(-0.096, cos.im(), 0.001);

    P.Complex tan = a.tan();
    assertEquals(0.519, tan.re(), 0.001);
    assertEquals(0.253, tan.im(), 0.001);
  }

  @Test
  public void testComplexEqualsAndHashCode() {
    P.Complex a1 = new P.Complex(1.0, 2.0);
    P.Complex a2 = new P.Complex(1.0, 2.0);
    P.Complex b = new P.Complex(2.0, 3.0);

    assertTrue(a1.equals(a2));
    assertFalse(a1.equals(b));
    assertEquals(a1.hashCode(), a2.hashCode());
  }

  // P.Ratio Tests
  @Test
  public void testRatioConversions() {
    P.Ratio r = new P.Ratio(BigInteger.valueOf(3), BigInteger.valueOf(2));
    assertEquals(1, r.intValue());
    assertEquals(1L, r.longValue());
    assertEquals(1.5f, r.floatValue(), 0.0f);
    assertEquals(1.5, r.doubleValue(), 0.0);
  }

  @Test
  public void testRatioEqualsAndHashCode() {
    P.Ratio r1 = new P.Ratio(BigInteger.valueOf(1), BigInteger.valueOf(2));
    P.Ratio r2 = new P.Ratio(BigInteger.valueOf(1), BigInteger.valueOf(2));
    P.Ratio r3 = new P.Ratio(BigInteger.valueOf(2), BigInteger.valueOf(3));

    assertTrue(r1.equals(r2));
    assertFalse(r1.equals(r3));
    assertEquals(r1.hashCode(), r2.hashCode());
  }

  @Test
  public void testRatioToString() {
    P.Ratio r = new P.Ratio(BigInteger.valueOf(5), BigInteger.valueOf(4));
    assertEquals("5/4", r.toString());
  }

  @Test
  public void testRatioCompareTo() {
    P.Ratio r1 = new P.Ratio(BigInteger.valueOf(1), BigInteger.valueOf(2));
    P.Ratio r2 = new P.Ratio(BigInteger.valueOf(3), BigInteger.valueOf(4));
    assertEquals(-1, r1.compareTo(r2));
    assertEquals(1, r2.compareTo(r1));
    assertEquals(0, r1.compareTo(new P.Ratio(BigInteger.valueOf(1), BigInteger.valueOf(2))));
    assertEquals(0, r1.compareTo(0.5));
  }
}
