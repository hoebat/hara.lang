package hara.lang.base;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Objects;

public interface P {

	public interface Bits {

		public static final byte deBruijnIndex[] = new byte[] { 0, 1, 2, 53, 3, 7, 54, 27, 4, 38, 41, 8, 34, 55, 48, 28,
				62, 5, 39, 46, 44, 42, 22, 9, 24, 35, 59, 56, 49, 18, 29, 11, 63, 52, 6, 26, 37, 40, 33, 47, 61, 45, 43,
				21, 23, 58, 17, 10, 51, 25, 36, 32, 60, 20, 57, 16, 50, 31, 19, 15, 30, 14, 13, 12 };

		/**
		 * @param n a number, which must be a power of two
		 * @return the offset of the bit
		 */
		public static int bitOffset(long n) {
			return deBruijnIndex[0xFF & (int) ((n * 0x022fdd63cc95386dL) >>> 58)];
		}

		/**
		 * @param n a number
		 * @return the same number, with all but the lowest bit zeroed out
		 */
		public static long lowestBit(long n) {
			return n & -n;
		}

		/**
		 * @param n a number
		 * @return the same number, with all but the lowest bit zeroed out
		 */
		public static int lowestBit(int n) {
			return n & -n;
		}

		/**
		 * @param n a number
		 * @return the same number, with all but the highest bit zeroed out
		 */
		public static long highestBit(long n) {
			return Long.highestOneBit(n);
		}

		/**
		 * @param n a number
		 * @return the same number, with all but the highest bit zeroed out
		 */
		public static int highestBit(int n) {
			return Integer.highestOneBit(n);
		}

		/**
		 * @param n a number
		 * @return the log2 of that value, rounded down
		 */
		public static int log2Floor(long n) {
			return bitOffset(highestBit(n));
		}

		/**
		 * @param n a number
		 * @return the log2 of the value, rounded up
		 */
		public static int log2Ceil(long n) {
			int log2 = log2Floor(n);
			return isPowerOfTwo(n) ? log2 : log2 + 1;
		}

		/**
		 * @param bits a bit offset
		 * @return a mask, with all bits below that offset set to one
		 */
		public static long maskBelow(int bits) {
			return (1L << bits) - 1;
		}

		/**
		 * @param bits a bit offset
		 * @return a mask, with all bits above that offset set to one
		 */
		public static long maskAbove(int bits) {
			return -1L & ~maskBelow(bits);
		}

		/**
		 * @return the offset of the highest bit which differs between {@code a} and
		 *         {@code b}
		 */
		public static int branchingBit(long a, long b) {
			if (a == b) {
				return -1;
			} else {
				return bitOffset(highestBit(a ^ b));
			}
		}

		/**
		 * @param n a number
		 * @return true, if the number is a power of two
		 */
		public static boolean isPowerOfTwo(long n) {
			return (n & (n - 1)) == 0;
		}

	}

	public interface Box {

		public static Object box(boolean x) {
			return x ? true : false;
		}

		public static Object box(Boolean x) {
			return x;
		}

		public static Number box(byte x) {
			return x;
		}

		public static Character box(char x) {
			return Character.valueOf(x);
		}

		public static Number box(double x) {
			return x;
		}

		public static Number box(float x) {
			return x;
		}

		public static Number box(int x) {
			return x;
		}

		public static Number box(long x) {
			return x;
		}

		public static Object box(Object x) {
			return x;
		}

		public static Number box(short x) {
			return x;
		}
	}

	public interface Cast {

		public static char charCast(Object x) {
			if (x instanceof Character)
				return ((Character) x).charValue();

			long n = ((Number) x).longValue();
			if (n < Character.MIN_VALUE || n > Character.MAX_VALUE)
				throw new IllegalArgumentException("Value out of range for char: " + x);

			return (char) n;
		}

		public static char charCast(byte x) {
			char i = (char) x;
			if (i != x)
				throw new IllegalArgumentException("Value out of range for char: " + x);
			return i;
		}

		public static char charCast(short x) {
			char i = (char) x;
			if (i != x)
				throw new IllegalArgumentException("Value out of range for char: " + x);
			return i;
		}

		public static char charCast(char x) {
			return x;
		}

		public static char charCast(int x) {
			char i = (char) x;
			if (i != x)
				throw new IllegalArgumentException("Value out of range for char: " + x);
			return i;
		}

		public static char charCast(long x) {
			char i = (char) x;
			if (i != x)
				throw new IllegalArgumentException("Value out of range for char: " + x);
			return i;
		}

		public static char charCast(float x) {
			if (x >= Character.MIN_VALUE && x <= Character.MAX_VALUE)
				return (char) x;
			throw new IllegalArgumentException("Value out of range for char: " + x);
		}

		public static char charCast(double x) {
			if (x >= Character.MIN_VALUE && x <= Character.MAX_VALUE)
				return (char) x;
			throw new IllegalArgumentException("Value out of range for char: " + x);
		}

		public static boolean booleanCast(Object x) {
			if (x instanceof Boolean)
				return ((Boolean) x).booleanValue();
			return x != null;
		}

		public static boolean booleanCast(boolean x) {
			return x;
		}

		public static byte byteCast(Object x) {
			if (x instanceof Byte)
				return ((Byte) x).byteValue();
			long n = longCast(x);
			if (n < Byte.MIN_VALUE || n > Byte.MAX_VALUE)
				throw new IllegalArgumentException("Value out of range for byte: " + x);

			return (byte) n;
		}

		public static byte byteCast(byte x) {
			return x;
		}

		public static byte byteCast(short x) {
			byte i = (byte) x;
			if (i != x)
				throw new IllegalArgumentException("Value out of range for byte: " + x);
			return i;
		}

		public static byte byteCast(int x) {
			byte i = (byte) x;
			if (i != x)
				throw new IllegalArgumentException("Value out of range for byte: " + x);
			return i;
		}

		public static byte byteCast(long x) {
			byte i = (byte) x;
			if (i != x)
				throw new IllegalArgumentException("Value out of range for byte: " + x);
			return i;
		}

		public static byte byteCast(float x) {
			if (x >= Byte.MIN_VALUE && x <= Byte.MAX_VALUE)
				return (byte) x;
			throw new IllegalArgumentException("Value out of range for byte: " + x);
		}

		public static byte byteCast(double x) {
			if (x >= Byte.MIN_VALUE && x <= Byte.MAX_VALUE)
				return (byte) x;
			throw new IllegalArgumentException("Value out of range for byte: " + x);
		}

		public static short shortCast(Object x) {
			if (x instanceof Short)
				return ((Short) x).shortValue();
			long n = longCast(x);
			if (n < Short.MIN_VALUE || n > Short.MAX_VALUE)
				throw new IllegalArgumentException("Value out of range for short: " + x);

			return (short) n;
		}

		public static short shortCast(byte x) {
			return x;
		}

		public static short shortCast(short x) {
			return x;
		}

		public static short shortCast(int x) {
			short i = (short) x;
			if (i != x)
				throw new IllegalArgumentException("Value out of range for short: " + x);
			return i;
		}

		public static short shortCast(long x) {
			short i = (short) x;
			if (i != x)
				throw new IllegalArgumentException("Value out of range for short: " + x);
			return i;
		}

		public static short shortCast(float x) {
			if (x >= Short.MIN_VALUE && x <= Short.MAX_VALUE)
				return (short) x;
			throw new IllegalArgumentException("Value out of range for short: " + x);
		}

		public static short shortCast(double x) {
			if (x >= Short.MIN_VALUE && x <= Short.MAX_VALUE)
				return (short) x;
			throw new IllegalArgumentException("Value out of range for short: " + x);
		}

		public static int intCast(Object x) {
			if (x instanceof Integer)
				return ((Integer) x).intValue();
			if (x instanceof Number) {
				long n = longCast(x);
				return intCast(n);
			}
			return ((Character) x).charValue();
		}

		public static int intCast(char x) {
			return x;
		}

		public static int intCast(byte x) {
			return x;
		}

		public static int intCast(short x) {
			return x;
		}

		public static int intCast(int x) {
			return x;
		}

		public static int intCast(float x) {
			if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE)
				throw new IllegalArgumentException("Value out of range for int: " + x);
			return (int) x;
		}

		public static int intCast(long x) {
			int i = (int) x;
			if (i != x)
				throw new IllegalArgumentException("Value out of range for int: " + x);
			return i;
		}

		public static int intCast(double x) {
			if (x < Integer.MIN_VALUE || x > Integer.MAX_VALUE)
				throw new IllegalArgumentException("Value out of range for int: " + x);
			return (int) x;
		}

		public static long longCast(Object x) {
			if (x instanceof Integer || x instanceof Long)
				return ((Number) x).longValue();
			else if (x instanceof BigInteger) {
				BigInteger bi = (BigInteger) x;
				if (bi.bitLength() < 64)
					return bi.longValue();
				else
					throw new IllegalArgumentException("Value out of range for long: " + x);
			} else if (x instanceof Byte || x instanceof Short)
				return ((Number) x).longValue();
			else if (x instanceof Character)
				return longCast(((Character) x).charValue());
			else
				return longCast(((Number) x).doubleValue());
		}

		public static long longCast(byte x) {
			return x;
		}

		public static long longCast(short x) {
			return x;
		}

		public static long longCast(int x) {
			return x;
		}

		public static long longCast(float x) {
			if (x < Long.MIN_VALUE || x > Long.MAX_VALUE)
				throw new IllegalArgumentException("Value out of range for long: " + x);
			return (long) x;
		}

		public static long longCast(long x) {
			return x;
		}

		public static long longCast(double x) {
			if (x < Long.MIN_VALUE || x > Long.MAX_VALUE)
				throw new IllegalArgumentException("Value out of range for long: " + x);
			return (long) x;
		}

		public static float floatCast(Object x) {
			if (x instanceof Float)
				return ((Float) x).floatValue();

			double n = ((Number) x).doubleValue();
			if (n < -Float.MAX_VALUE || n > Float.MAX_VALUE)
				throw new IllegalArgumentException("Value out of range for float: " + x);

			return (float) n;

		}

		public static float floatCast(byte x) {
			return x;
		}

		public static float floatCast(short x) {
			return x;
		}

		public static float floatCast(int x) {
			return x;
		}

		public static float floatCast(float x) {
			return x;
		}

		public static float floatCast(long x) {
			return x;
		}

		public static float floatCast(double x) {
			if (x < -Float.MAX_VALUE || x > Float.MAX_VALUE)
				throw new IllegalArgumentException("Value out of range for float: " + x);

			return (float) x;
		}

		public static double doubleCast(Object x) {
			return ((Number) x).doubleValue();
		}

		public static double doubleCast(byte x) {
			return x;
		}

		public static double doubleCast(short x) {
			return x;
		}

		public static double doubleCast(int x) {
			return x;
		}

		public static double doubleCast(float x) {
			return x;
		}

		public static double doubleCast(long x) {
			return x;
		}

		public static double doubleCast(double x) {
			return x;
		}

		public static byte uncheckedByteCast(Object x) {
			return ((Number) x).byteValue();
		}

		public static byte uncheckedByteCast(byte x) {
			return x;
		}

		public static byte uncheckedByteCast(short x) {
			return (byte) x;
		}

		public static byte uncheckedByteCast(int x) {
			return (byte) x;
		}

		public static byte uncheckedByteCast(long x) {
			return (byte) x;
		}

		public static byte uncheckedByteCast(float x) {
			return (byte) x;
		}

		public static byte uncheckedByteCast(double x) {
			return (byte) x;
		}

		public static short uncheckedShortCast(Object x) {
			return ((Number) x).shortValue();
		}

		public static short uncheckedShortCast(byte x) {
			return x;
		}

		public static short uncheckedShortCast(short x) {
			return x;
		}

		public static short uncheckedShortCast(int x) {
			return (short) x;
		}

		public static short uncheckedShortCast(long x) {
			return (short) x;
		}

		public static short uncheckedShortCast(float x) {
			return (short) x;
		}

		public static short uncheckedShortCast(double x) {
			return (short) x;
		}

		public static char uncheckedCharCast(Object x) {
			if (x instanceof Character)
				return ((Character) x).charValue();
			return (char) ((Number) x).longValue();
		}

		public static char uncheckedCharCast(byte x) {
			return (char) x;
		}

		public static char uncheckedCharCast(short x) {
			return (char) x;
		}

		public static char uncheckedCharCast(char x) {
			return x;
		}

		public static char uncheckedCharCast(int x) {
			return (char) x;
		}

		public static char uncheckedCharCast(long x) {
			return (char) x;
		}

		public static char uncheckedCharCast(float x) {
			return (char) x;
		}

		public static char uncheckedCharCast(double x) {
			return (char) x;
		}

		public static int uncheckedIntCast(Object x) {
			if (x instanceof Number)
				return ((Number) x).intValue();
			return ((Character) x).charValue();
		}

		public static int uncheckedIntCast(byte x) {
			return x;
		}

		public static int uncheckedIntCast(short x) {
			return x;
		}

		public static int uncheckedIntCast(char x) {
			return x;
		}

		public static int uncheckedIntCast(int x) {
			return x;
		}

		public static int uncheckedIntCast(long x) {
			return (int) x;
		}

		public static int uncheckedIntCast(float x) {
			return (int) x;
		}

		public static int uncheckedIntCast(double x) {
			return (int) x;
		}

		public static long uncheckedLongCast(Object x) {
			return ((Number) x).longValue();
		}

		public static long uncheckedLongCast(byte x) {
			return x;
		}

		public static long uncheckedLongCast(short x) {
			return x;
		}

		public static long uncheckedLongCast(int x) {
			return x;
		}

		public static long uncheckedLongCast(long x) {
			return x;
		}

		public static long uncheckedLongCast(float x) {
			return (long) x;
		}

		public static long uncheckedLongCast(double x) {
			return (long) x;
		}

		public static float uncheckedFloatCast(Object x) {
			return ((Number) x).floatValue();
		}

		public static float uncheckedFloatCast(byte x) {
			return x;
		}

		public static float uncheckedFloatCast(short x) {
			return x;
		}

		public static float uncheckedFloatCast(int x) {
			return x;
		}

		public static float uncheckedFloatCast(long x) {
			return x;
		}

		public static float uncheckedFloatCast(float x) {
			return x;
		}

		public static float uncheckedFloatCast(double x) {
			return (float) x;
		}

		public static double uncheckedDoubleCast(Object x) {
			return ((Number) x).doubleValue();
		}

		public static double uncheckedDoubleCast(byte x) {
			return x;
		}

		public static double uncheckedDoubleCast(short x) {
			return x;
		}

		public static double uncheckedDoubleCast(int x) {
			return x;
		}

		public static double uncheckedDoubleCast(long x) {
			return x;
		}

		public static double uncheckedDoubleCast(float x) {
			return x;
		}

		public static double uncheckedDoubleCast(double x) {
			return x;
		}
	}

	public class Complex {
		private final double re; // the real part
		private final double im; // the imaginary part

		// create a new object with the given real and imaginary parts
		public Complex(double real, double imag) {
			re = real;
			im = imag;
		}

		// return a string representation of the invoking Complex object
		@Override
		public String toString() {
			if (im == 0)
				return re + "";
			if (re == 0)
				return im + "i";
			if (im < 0)
				return re + " - " + (-im) + "i";
			return re + " + " + im + "i";
		}

		// return abs/modulus/magnitude
		public double abs() {
			return Math.hypot(re, im);
		}

		// return angle/phase/argument, normalized to be between -pi and pi
		public double phase() {
			return Math.atan2(im, re);
		}

		// return a new Complex object whose value is (this + b)
		public Complex plus(Complex b) {
			Complex a = this; // invoking object
			double real = a.re + b.re;
			double imag = a.im + b.im;
			return new Complex(real, imag);
		}

		// return a new Complex object whose value is (this - b)
		public Complex minus(Complex b) {
			Complex a = this;
			double real = a.re - b.re;
			double imag = a.im - b.im;
			return new Complex(real, imag);
		}

		// return a new Complex object whose value is (this * b)
		public Complex times(Complex b) {
			Complex a = this;
			double real = a.re * b.re - a.im * b.im;
			double imag = a.re * b.im + a.im * b.re;
			return new Complex(real, imag);
		}

		// return a new object whose value is (this * alpha)
		public Complex scale(double alpha) {
			return new Complex(alpha * re, alpha * im);
		}

		// return a new Complex object whose value is the conjugate of this
		public Complex conjugate() {
			return new Complex(re, -im);
		}

		// return a new Complex object whose value is the reciprocal of this
		public Complex reciprocal() {
			double scale = re * re + im * im;
			return new Complex(re / scale, -im / scale);
		}

		// return the real or imaginary part
		public double re() {
			return re;
		}

		public double im() {
			return im;
		}

		// return a / b
		public Complex divides(Complex b) {
			Complex a = this;
			return a.times(b.reciprocal());
		}

		// return a new Complex object whose value is the complex exponential of this
		public Complex exp() {
			return new Complex(Math.exp(re) * Math.cos(im), Math.exp(re) * Math.sin(im));
		}

		// return a new Complex object whose value is the complex sine of this
		public Complex sin() {
			return new Complex(Math.sin(re) * Math.cosh(im), Math.cos(re) * Math.sinh(im));
		}

		// return a new Complex object whose value is the complex cosine of this
		public Complex cos() {
			return new Complex(Math.cos(re) * Math.cosh(im), -Math.sin(re) * Math.sinh(im));
		}

		// return a new Complex object whose value is the complex tangent of this
		public Complex tan() {
			return sin().divides(cos());
		}

		// a static version of plus
		public static Complex plus(Complex a, Complex b) {
			double real = a.re + b.re;
			double imag = a.im + b.im;
			Complex sum = new Complex(real, imag);
			return sum;
		}

		// See Section 3.3.
		@Override
		public boolean equals(Object x) {
			if (x == null)
				return false;
			if (this.getClass() != x.getClass())
				return false;
			Complex that = (Complex) x;
			return (this.re == that.re) && (this.im == that.im);
		}

		// See Section 3.3.
		@Override
		public int hashCode() {
			return Objects.hash(re, im);
		}

	}

	public class Ratio extends Number implements Comparable<Object> {

		private static final long serialVersionUID = 1L;
		final public BigInteger numerator;
		final public BigInteger denominator;

		public Ratio(BigInteger numerator, BigInteger denominator) {
			this.numerator = numerator;
			this.denominator = denominator;
		}

		@Override
		public boolean equals(Object arg0) {
			return arg0 != null && arg0 instanceof Ratio && ((Ratio) arg0).numerator.equals(numerator)
					&& ((Ratio) arg0).denominator.equals(denominator);
		}

		@Override
		public int hashCode() {
			return numerator.hashCode() ^ denominator.hashCode();
		}

		@Override
		public String toString() {
			return numerator.toString() + "/" + denominator.toString();
		}

		@Override
		public int intValue() {
			return (int) doubleValue();
		}

		@Override
		public long longValue() {
			return bigIntegerValue().longValue();
		}

		@Override
		public float floatValue() {
			return (float) doubleValue();
		}

		@Override
		public double doubleValue() {
			return decimalValue(MathContext.DECIMAL64).doubleValue();
		}

		public BigDecimal decimalValue() {
			return decimalValue(MathContext.UNLIMITED);
		}

		public BigDecimal decimalValue(MathContext mc) {
			BigDecimal numerator = new BigDecimal(this.numerator);
			BigDecimal denominator = new BigDecimal(this.denominator);

			return numerator.divide(denominator, mc);
		}

		public BigInteger bigIntegerValue() {
			return numerator.divide(denominator);
		}

		@Override
		public int compareTo(Object o) {
			Number other = (Number) o;
			return Num.compare(this, other);
		}
	}
}
