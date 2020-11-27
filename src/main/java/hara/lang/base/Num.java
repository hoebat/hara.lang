package hara.lang.base;

import java.math.BigInteger;
import java.math.BigDecimal;
import java.math.MathContext;

import hara.lang.base.P.Ratio;

public interface Num {

	final static class BigDecimalOps extends OpsP {
		
		// final static Var MATH_CONTEXT = Ut.MATH_CONTEXT;

		@Override
		final public Number add(Number x, Number y) {
			MathContext mc = null; // (MathContext) MATH_CONTEXT.deref();
			return mc == null ? toBigDecimal(x).add(toBigDecimal(y)) : toBigDecimal(x).add(toBigDecimal(y), mc);
		}

		@Override
		public Ops combine(Ops y) {
			return y.opsWith(this);
		}

		@Override
		public Number dec(Number x) {
			MathContext mc = null; // (MathContext) MATH_CONTEXT.deref();
			BigDecimal bx = (BigDecimal) x;
			return mc == null ? bx.subtract(BigDecimal.ONE) : bx.subtract(BigDecimal.ONE, mc);
		}

		@Override
		public Number divide(Number x, Number y) {
			MathContext mc = null; // (MathContext) MATH_CONTEXT.deref();
			return mc == null ? toBigDecimal(x).divide(toBigDecimal(y)) : toBigDecimal(x).divide(toBigDecimal(y), mc);
		}

		@Override
		public boolean eq(Number x, Number y) {
			return toBigDecimal(x).compareTo(toBigDecimal(y)) == 0;
		}

		@Override
		public boolean gte(Number x, Number y) {
			return toBigDecimal(x).compareTo(toBigDecimal(y)) >= 0;
		}

		@Override
		public Number inc(Number x) {
			MathContext mc = null; // (MathContext) MATH_CONTEXT.deref();
			BigDecimal bx = (BigDecimal) x;
			return mc == null ? bx.add(BigDecimal.ONE) : bx.add(BigDecimal.ONE, mc);
		}

		@Override
		public boolean isNeg(Number x) {
			BigDecimal bx = (BigDecimal) x;
			return bx.signum() < 0;
		}

		@Override
		public boolean isPos(Number x) {
			BigDecimal bx = (BigDecimal) x;
			return bx.signum() > 0;
		}

		@Override
		public boolean isZero(Number x) {
			BigDecimal bx = (BigDecimal) x;
			return bx.signum() == 0;
		}

		@Override
		public boolean lt(Number x, Number y) {
			return toBigDecimal(x).compareTo(toBigDecimal(y)) < 0;
		}

		@Override
		public boolean lte(Number x, Number y) {
			return toBigDecimal(x).compareTo(toBigDecimal(y)) <= 0;
		}

		@Override
		final public Number multiply(Number x, Number y) {
			MathContext mc = null; // (MathContext) MATH_CONTEXT.deref();
			return mc == null ? toBigDecimal(x).multiply(toBigDecimal(y))
					: toBigDecimal(x).multiply(toBigDecimal(y), mc);
		}

		// public Number subtract(Number x, Number y);
		@Override
		final public Number negate(Number x) {
			MathContext mc = null; // (MathContext) MATH_CONTEXT.deref();
			return mc == null ? ((BigDecimal) x).negate() : ((BigDecimal) x).negate(mc);
		}

		@Override
		final public Ops opsWith(BigDecimalOps x) {
			return this;
		}

		@Override
		final public Ops opsWith(BigIntegerOps x) {
			return this;
		}

		@Override
		final public Ops opsWith(DoubleOps x) {
			return DOUBLE_OPS;
		}

		@Override
		final public Ops opsWith(LongOps x) {
			return this;
		}

		@Override
		final public Ops opsWith(RatioOps x) {
			return this;
		}

		@Override
		public Number quotient(Number x, Number y) {
			MathContext mc = null; // (MathContext) MATH_CONTEXT.deref();
			return mc == null ? toBigDecimal(x).divideToIntegralValue(toBigDecimal(y))
					: toBigDecimal(x).divideToIntegralValue(toBigDecimal(y), mc);
		}

		@Override
		public Number remainder(Number x, Number y) {
			MathContext mc = null; // (MathContext) MATH_CONTEXT.deref();
			return mc == null ? toBigDecimal(x).remainder(toBigDecimal(y))
					: toBigDecimal(x).remainder(toBigDecimal(y), mc);
		}
	}

	final static class BigIntegerOps extends OpsP {
		@Override
		final public Number add(Number x, Number y) {
			return toBigInteger(x).add(toBigInteger(y));
		}

		@Override
		public Ops combine(Ops y) {
			return y.opsWith(this);
		}

		@Override
		public Number dec(Number x) {
			BigInteger bx = toBigInteger(x);
			return bx.subtract(BigInteger.ONE);
		}

		@Override
		public Number divide(Number x, Number y) {
			return Num.divide(toBigInteger(x), toBigInteger(y));
		}

		@Override
		public boolean eq(Number x, Number y) {
			return toBigInteger(x).equals(toBigInteger(y));
		}

		@Override
		public boolean gte(Number x, Number y) {
			return toBigInteger(x).compareTo(toBigInteger(y)) >= 0;
		}

		@Override
		public Number inc(Number x) {
			BigInteger bx = toBigInteger(x);
			return bx.add(BigInteger.ONE);
		}

		@Override
		public boolean isNeg(Number x) {
			BigInteger bx = toBigInteger(x);
			return bx.signum() < 0;
		}

		@Override
		public boolean isPos(Number x) {
			BigInteger bx = toBigInteger(x);
			return bx.signum() > 0;
		}

		@Override
		public boolean isZero(Number x) {
			BigInteger bx = toBigInteger(x);
			return bx.signum() == 0;
		}

		@Override
		public boolean lt(Number x, Number y) {
			return toBigInteger(x).compareTo(toBigInteger(y)) == -1;
		}

		@Override
		public boolean lte(Number x, Number y) {
			return toBigInteger(x).compareTo(toBigInteger(y)) <= 0;
		}

		@Override
		final public Number multiply(Number x, Number y) {
			return toBigInteger(x).multiply(toBigInteger(y));
		}

		// public Number subtract(Number x, Number y);
		@Override
		final public Number negate(Number x) {
			return toBigInteger(x).negate();
		}

		@Override
		final public Ops opsWith(BigDecimalOps x) {
			return BIGDECIMAL_OPS;
		}

		@Override
		final public Ops opsWith(BigIntegerOps x) {
			return this;
		}

		@Override
		final public Ops opsWith(DoubleOps x) {
			return DOUBLE_OPS;
		}

		@Override
		final public Ops opsWith(LongOps x) {
			return this;
		}

		@Override
		final public Ops opsWith(RatioOps x) {
			return RATIO_OPS;
		}

		@Override
		public Number quotient(Number x, Number y) {
			return toBigInteger(x).divide(toBigInteger(y));
		}

		@Override
		public Number remainder(Number x, Number y) {
			return toBigInteger(x).remainder(toBigInteger(y));
		}
	}

	public static enum Category {
		DECIMAL, FLOATING, INTEGER, RATIO
	}

	final static class DoubleOps extends OpsP {
		@Override
		final public Number add(Number x, Number y) {
			return Double.valueOf(x.doubleValue() + y.doubleValue());
		}

		@Override
		public Ops combine(Ops y) {
			return y.opsWith(this);
		}

		@Override
		public Number dec(Number x) {
			return Double.valueOf(x.doubleValue() - 1);
		}

		@Override
		public Number divide(Number x, Number y) {
			return Double.valueOf(x.doubleValue() / y.doubleValue());
		}

		@Override
		public boolean eq(Number x, Number y) {
			return x.doubleValue() == y.doubleValue();
		}

		@Override
		public boolean gte(Number x, Number y) {
			return x.doubleValue() >= y.doubleValue();
		}

		@Override
		public Number inc(Number x) {
			return Double.valueOf(x.doubleValue() + 1);
		}

		@Override
		public boolean isNeg(Number x) {
			return x.doubleValue() < 0;
		}

		@Override
		public boolean isPos(Number x) {
			return x.doubleValue() > 0;
		}

		@Override
		public boolean isZero(Number x) {
			return x.doubleValue() == 0;
		}

		@Override
		public boolean lt(Number x, Number y) {
			return x.doubleValue() < y.doubleValue();
		}

		@Override
		public boolean lte(Number x, Number y) {
			return x.doubleValue() <= y.doubleValue();
		}

		@Override
		final public Number multiply(Number x, Number y) {
			return Double.valueOf(x.doubleValue() * y.doubleValue());
		}

		// public Number subtract(Number x, Number y);
		@Override
		final public Number negate(Number x) {
			return Double.valueOf(-x.doubleValue());
		}

		@Override
		final public Ops opsWith(BigDecimalOps x) {
			return this;
		}

		@Override
		final public Ops opsWith(BigIntegerOps x) {
			return this;
		}

		@Override
		final public Ops opsWith(DoubleOps x) {
			return this;
		}

		@Override
		final public Ops opsWith(LongOps x) {
			return this;
		}

		@Override
		final public Ops opsWith(RatioOps x) {
			return this;
		}

		@Override
		public Number quotient(Number x, Number y) {
			return Num.quotient(x.doubleValue(), y.doubleValue());
		}

		@Override
		public Number remainder(Number x, Number y) {
			return Num.remainder(x.doubleValue(), y.doubleValue());
		}
	}

	final static class LongOps implements Ops {
		static long gcd(long u, long v) {
			while (v != 0) {
				long r = u % v;
				u = v;
				v = r;
			}
			return u;
		}

		@Override
		final public Number add(Number x, Number y) {
			return num(Num.add(x.longValue(), y.longValue()));
		}

		@Override
		final public Number addP(Number x, Number y) {
			long lx = x.longValue(), ly = y.longValue();
			long ret = lx + ly;
			if ((ret ^ lx) < 0 && (ret ^ ly) < 0)
				return BIGINT_OPS.add(x, y);
			return num(ret);
		}

		@Override
		public Ops combine(Ops y) {
			return y.opsWith(this);
		}

		@Override
		public Number dec(Number x) {
			long val = x.longValue();
			return num(Num.dec(val));
		}

		@Override
		public Number decP(Number x) {
			long val = x.longValue();
			if (val > Long.MIN_VALUE)
				return num(val - 1);
			return BIGINT_OPS.dec(x);
		}

		@Override
		public Number divide(Number x, Number y) {
			long n = x.longValue();
			long val = y.longValue();
			long gcd = gcd(n, val);
			if (gcd == 0)
				return num(0);

			n = n / gcd;
			long d = val / gcd;
			if (d == 1)
				return num(n);
			if (d < 0) {
				n = -n;
				d = -d;
			}
			return new Ratio(BigInteger.valueOf(n), BigInteger.valueOf(d));
		}

		@Override
		public boolean eq(Number x, Number y) {
			return x.longValue() == y.longValue();
		}

		@Override
		public boolean gte(Number x, Number y) {
			return x.longValue() >= y.longValue();
		}

		@Override
		public Number inc(Number x) {
			long val = x.longValue();
			return num(Num.inc(val));
		}

		@Override
		public Number incP(Number x) {
			long val = x.longValue();
			if (val < Long.MAX_VALUE)
				return num(val + 1);
			return BIGINT_OPS.inc(x);
		}

		@Override
		public boolean isNeg(Number x) {
			return x.longValue() < 0;
		}

		@Override
		public boolean isPos(Number x) {
			return x.longValue() > 0;
		}

		@Override
		public boolean isZero(Number x) {
			return x.longValue() == 0;
		}

		@Override
		public boolean lt(Number x, Number y) {
			return x.longValue() < y.longValue();
		}

		@Override
		public boolean lte(Number x, Number y) {
			return x.longValue() <= y.longValue();
		}

		@Override
		final public Number multiply(Number x, Number y) {
			return num(Num.multiply(x.longValue(), y.longValue()));
		}

		@Override
		final public Number multiplyP(Number x, Number y) {
			long lx = x.longValue(), ly = y.longValue();
			if (lx == Long.MIN_VALUE && ly < 0)
				return BIGINT_OPS.multiply(x, y);
			long ret = lx * ly;
			if (ly != 0 && ret / ly != lx)
				return BIGINT_OPS.multiply(x, y);
			return num(ret);
		}

		// public Number subtract(Number x, Number y);
		@Override
		final public Number negate(Number x) {
			long val = x.longValue();
			return num(Num.minus(val));
		}

		@Override
		final public Number negateP(Number x) {
			long val = x.longValue();
			if (val > Long.MIN_VALUE)
				return num(-val);
			return BigInteger.valueOf(val).negate();
		}

		@Override
		final public Ops opsWith(BigDecimalOps x) {
			return BIGDECIMAL_OPS;
		}

		@Override
		final public Ops opsWith(BigIntegerOps x) {
			return BIGINT_OPS;
		}

		@Override
		final public Ops opsWith(DoubleOps x) {
			return DOUBLE_OPS;
		}

		@Override
		final public Ops opsWith(LongOps x) {
			return this;
		}

		@Override
		final public Ops opsWith(RatioOps x) {
			return RATIO_OPS;
		}

		@Override
		public Number quotient(Number x, Number y) {
			return num(x.longValue() / y.longValue());
		}

		@Override
		public Number remainder(Number x, Number y) {
			return num(x.longValue() % y.longValue());
		}

		@Override
		final public Number unchecked_add(Number x, Number y) {
			return num(Num.unchecked_add(x.longValue(), y.longValue()));
		}

		@Override
		public Number unchecked_dec(Number x) {
			long val = x.longValue();
			return num(Num.unchecked_dec(val));
		}

		@Override
		public Number unchecked_inc(Number x) {
			long val = x.longValue();
			return num(Num.unchecked_inc(val));
		}

		@Override
		final public Number unchecked_multiply(Number x, Number y) {
			return num(Num.unchecked_multiply(x.longValue(), y.longValue()));
		}

		@Override
		final public Number unchecked_negate(Number x) {
			long val = x.longValue();
			return num(Num.unchecked_minus(val));
		}
	}

	public interface Max {

	public static double max(double x, double y) {
		return Math.max(x, y);
	}

	public static Object max(double x, long y) {
		if (Double.isNaN(x)) {
			return x;
		}
		if (x > y) {
			return x;
		} else {
			return y;
		}
	}

	public static Object max(double x, Object y) {
		if (Double.isNaN(x)) {
			return x;
		} else if (isNaN(y)) {
			return y;
		}
		if (x > ((Number) y).doubleValue()) {
			return x;
		} else {
			return y;
		}
	}

	public static Object max(long x, double y) {
		if (Double.isNaN(y)) {
			return y;
		}
		if (x > y) {
			return x;
		} else {
			return y;
		}
	}

	public static long max(long x, long y) {
		if (x > y) {
			return x;
		} else {
			return y;
		}
	}

	public static Object max(long x, Object y) {
		if (isNaN(y)) {
			return y;
		}
		if (gt(x, y)) {
			return x;
		} else {
			return y;
		}
	}

	public static Object max(Object x, double y) {
		if (isNaN(x)) {
			return x;
		} else if (Double.isNaN(y)) {
			return y;
		}
		if (((Number) x).doubleValue() > y) {
			return x;
		} else {
			return y;
		}
	}

	public static Object max(Object x, long y) {
		if (isNaN(x)) {
			return x;
		}
		if (gt(x, y)) {
			return x;
		} else {
			return y;
		}
	}

	public static Object max(Object x, Object y) {
		if (isNaN(x)) {
			return x;
		} else if (isNaN(y)) {
			return y;
		}
		if (gt(x, y)) {
			return x;
		} else {
			return y;
		}
	}
	}

	public interface Min {
	public static double min(double x, double y) {
		return Math.min(x, y);
	}

	public static Object min(double x, long y) {
		if (Double.isNaN(x)) {
			return x;
		}
		if (x < y) {
			return x;
		} else {
			return y;
		}
	}

	public static Object min(double x, Object y) {
		if (Double.isNaN(x)) {
			return x;
		} else if (isNaN(y)) {
			return y;
		}
		if (x < ((Number) y).doubleValue()) {
			return x;
		} else {
			return y;
		}
	}

	public static Object min(long x, double y) {
		if (Double.isNaN(y)) {
			return y;
		}
		if (x < y) {
			return x;
		} else {
			return y;
		}
	}

	public static long min(long x, long y) {
		if (x < y) {
			return x;
		} else {
			return y;
		}
	}

	public static Object min(long x, Object y) {
		if (isNaN(y)) {
			return y;
		}
		if (lt(x, y)) {
			return x;
		} else {
			return y;
		}
	}

	public static Object min(Object x, double y) {
		if (isNaN(x)) {
			return x;
		} else if (Double.isNaN(y)) {
			return y;
		}
		if (((Number) x).doubleValue() < y) {
			return x;
		} else {
			return y;
		}
	}

	public static Object min(Object x, long y) {
		if (isNaN(x)) {
			return x;
		}
		if (lt(x, y)) {
			return x;
		} else {
			return y;
		}
	}

	public static Object min(Object x, Object y) {
		if (isNaN(x)) {
			return x;
		} else if (isNaN(y)) {
			return y;
		}
		if (lt(x, y)) {
			return x;
		} else {
			return y;
		}
	}
	}

	static interface Ops {
		public Number add(Number x, Number y);

		public Number addP(Number x, Number y);

		Ops combine(Ops y);

		public Number dec(Number x);

		public Number decP(Number x);

		public Number divide(Number x, Number y);

		public boolean eq(Number x, Number y);

		public boolean gte(Number x, Number y);

		public Number inc(Number x);

		public Number incP(Number x);

		public boolean isNeg(Number x);

		public boolean isPos(Number x);

		public boolean isZero(Number x);

		public boolean lt(Number x, Number y);

		public boolean lte(Number x, Number y);

		public Number multiply(Number x, Number y);

		public Number multiplyP(Number x, Number y);

		public Number negate(Number x);

		public Number negateP(Number x);

		Ops opsWith(BigDecimalOps x);

		Ops opsWith(BigIntegerOps x);

		Ops opsWith(DoubleOps x);

		Ops opsWith(LongOps x);

		Ops opsWith(RatioOps x);

		public Number quotient(Number x, Number y);

		public Number remainder(Number x, Number y);

		public Number unchecked_add(Number x, Number y);

		public Number unchecked_dec(Number x);

		public Number unchecked_inc(Number x);

		public Number unchecked_multiply(Number x, Number y);

		public Number unchecked_negate(Number x);
	}

	static abstract class OpsP implements Ops {
		@Override
		public Number addP(Number x, Number y) {
			return add(x, y);
		}

		@Override
		public Number decP(Number x) {
			return dec(x);
		}

		@Override
		public Number incP(Number x) {
			return inc(x);
		}

		@Override
		public Number multiplyP(Number x, Number y) {
			return multiply(x, y);
		}

		@Override
		public Number negateP(Number x) {
			return negate(x);
		}

		@Override
		public Number unchecked_add(Number x, Number y) {
			return add(x, y);
		}

		@Override
		public Number unchecked_dec(Number x) {
			return dec(x);
		}

		@Override
		public Number unchecked_inc(Number x) {
			return inc(x);
		}

		@Override
		public Number unchecked_multiply(Number x, Number y) {
			return multiply(x, y);
		}

		@Override
		public Number unchecked_negate(Number x) {
			return negate(x);
		}

	}

	final static class RatioOps extends OpsP {
		static Number normalizeRet(Number ret, Number x, Number y) {
//		if(ret instanceof BigInteger && !(x instanceof BigInteger || y instanceof BigInteger))
//			{
//			return reduceBigInteger((BigInteger) ret);
//			}
			return ret;
		}

		@Override
		final public Number add(Number x, Number y) {
			Ratio rx = toRatio(x);
			Ratio ry = toRatio(y);
			Number ret = divide(ry.numerator.multiply(rx.denominator).add(rx.numerator.multiply(ry.denominator)),
					ry.denominator.multiply(rx.denominator));
			return normalizeRet(ret, x, y);
		}

		@Override
		public Ops combine(Ops y) {
			return y.opsWith(this);
		}

		@Override
		public Number dec(Number x) {
			return Num.add(x, -1);
		}

		@Override
		public Number divide(Number x, Number y) {
			Ratio rx = toRatio(x);
			Ratio ry = toRatio(y);
			Number ret = Num.divide(ry.denominator.multiply(rx.numerator), ry.numerator.multiply(rx.denominator));
			return normalizeRet(ret, x, y);
		}

		@Override
		public boolean eq(Number x, Number y) {
			Ratio rx = toRatio(x);
			Ratio ry = toRatio(y);
			return rx.numerator.equals(ry.numerator) && rx.denominator.equals(ry.denominator);
		}

		@Override
		public boolean gte(Number x, Number y) {
			Ratio rx = toRatio(x);
			Ratio ry = toRatio(y);
			return Num.gte(rx.numerator.multiply(ry.denominator), ry.numerator.multiply(rx.denominator));
		}

		@Override
		public Number inc(Number x) {
			return Num.add(x, 1);
		}

		@Override
		public boolean isNeg(Number x) {
			Ratio r = (Ratio) x;
			return r.numerator.signum() < 0;
		}

		@Override
		public boolean isPos(Number x) {
			Ratio r = (Ratio) x;
			return r.numerator.signum() > 0;
		}

		@Override
		public boolean isZero(Number x) {
			Ratio r = (Ratio) x;
			return r.numerator.signum() == 0;
		}

		@Override
		public boolean lt(Number x, Number y) {
			Ratio rx = toRatio(x);
			Ratio ry = toRatio(y);
			return Num.lt(rx.numerator.multiply(ry.denominator), ry.numerator.multiply(rx.denominator));
		}

		@Override
		public boolean lte(Number x, Number y) {
			Ratio rx = toRatio(x);
			Ratio ry = toRatio(y);
			return Num.lte(rx.numerator.multiply(ry.denominator), ry.numerator.multiply(rx.denominator));
		}

		@Override
		final public Number multiply(Number x, Number y) {
			Ratio rx = toRatio(x);
			Ratio ry = toRatio(y);
			Number ret = Num.divide(ry.numerator.multiply(rx.numerator), ry.denominator.multiply(rx.denominator));
			return normalizeRet(ret, x, y);
		}

		// public Number subtract(Number x, Number y);
		@Override
		final public Number negate(Number x) {
			Ratio r = (Ratio) x;
			return new Ratio(r.numerator.negate(), r.denominator);
		}

		@Override
		final public Ops opsWith(BigDecimalOps x) {
			return BIGDECIMAL_OPS;
		}

		@Override
		final public Ops opsWith(BigIntegerOps x) {
			return this;
		}

		@Override
		final public Ops opsWith(DoubleOps x) {
			return DOUBLE_OPS;
		}

		@Override
		final public Ops opsWith(LongOps x) {
			return this;
		}

		@Override
		final public Ops opsWith(RatioOps x) {
			return this;
		}

		@Override
		public Number quotient(Number x, Number y) {
			Ratio rx = toRatio(x);
			Ratio ry = toRatio(y);
			BigInteger q = rx.numerator.multiply(ry.denominator).divide(rx.denominator.multiply(ry.numerator));
			return normalizeRet(q, x, y);
		}

		@Override
		public Number remainder(Number x, Number y) {
			Ratio rx = toRatio(x);
			Ratio ry = toRatio(y);
			BigInteger q = rx.numerator.multiply(ry.denominator).divide(rx.denominator.multiply(ry.numerator));
			Number ret = Num.minus(x, Num.multiply(q, y));
			return normalizeRet(ret, x, y);
		}

	}

	static final BigDecimalOps BIGDECIMAL_OPS = new BigDecimalOps();

	static final BigIntegerOps BIGINT_OPS = new BigIntegerOps();

	static final DoubleOps DOUBLE_OPS = new DoubleOps();

	static final LongOps LONG_OPS = new LongOps();

	static final RatioOps RATIO_OPS = new RatioOps();

	public static double add(double x, double y) {
		return x + y;
	}

	public static double add(double x, long y) {
		return x + y;
	}

	public static double add(double x, Object y) {
		return add(x, ((Number) y).doubleValue());
	}

	public static double add(long x, double y) {
		return x + y;
	}

	public static long add(long x, long y) {
		long ret = x + y;
		if ((ret ^ x) < 0 && (ret ^ y) < 0)
			return throwIntOverflow();
		return ret;
	}

	public static Number add(long x, Object y) {
		return add((Object) x, y);
	}

	public static double add(Object x, double y) {
		return add(((Number) x).doubleValue(), y);
	}

	public static Number add(Object x, long y) {
		return add(x, (Object) y);
	}

	public static Number add(Object x, Object y) {
		return ops(x).combine(ops(y)).add((Number) x, (Number) y);
	}

	public static double addP(double x, double y) {
		return x + y;
	}

	public static double addP(double x, long y) {
		return x + y;
	}

	public static double addP(double x, Object y) {
		return addP(x, ((Number) y).doubleValue());
	}

	public static double addP(long x, double y) {
		return x + y;
	}

	public static Number addP(long x, long y) {
		long ret = x + y;
		if ((ret ^ x) < 0 && (ret ^ y) < 0)
			return addP((Number) x, (Number) y);
		return num(ret);
	}

	public static Number addP(long x, Object y) {
		return addP((Object) x, y);
	}

	public static double addP(Object x, double y) {
		return addP(((Number) x).doubleValue(), y);
	}

	public static Number addP(Object x, long y) {
		return addP(x, (Object) y);
	}

	public static Number addP(Object x, Object y) {
		return ops(x).combine(ops(y)).addP((Number) x, (Number) y);
	}

	public static long and(long x, long y) {
		return x & y;
	}

//static  Number box(int val){
//		return Integer.valueOf(val);
//}

//static  Number box(long val){
//		return Long.valueOf(val);
//}
//
//static  Double box(double val){
//		return Double.valueOf(val);
//}
//
//static  Double box(float val){
//		return Double.valueOf((double) val);
//}

	/*
	// @WarnBoxedMath(false)
	public static Number reduceBigInteger(BigInteger val) {
		if (val.bipart == null)
			return num(val.lpart);
		else
			return val.bipart;
	}
	*/

	public static long and(long x, Object y) {
		return and(x, bitOpsCast(y));
	}

	public static long and(Object x, long y) {
		return and(bitOpsCast(x), y);
	}

	public static long and(Object x, Object y) {
		return and(bitOpsCast(x), bitOpsCast(y));
	}

	public static long andNot(long x, long y) {
		return x & ~y;
	}

	public static long andNot(long x, Object y) {
		return andNot(x, bitOpsCast(y));
	}

	public static long andNot(Object x, long y) {
		return andNot(bitOpsCast(x), y);
	}

	public static long andNot(Object x, Object y) {
		return andNot(bitOpsCast(x), bitOpsCast(y));
	}

	static long bitOpsCast(Object x) {
		Class<? extends Object> xc = x.getClass();

		if (xc == Long.class || xc == Integer.class || xc == Short.class || xc == Byte.class)
			return P.Cast.longCast(x);
		// no bignums, no decimals
		throw new IllegalArgumentException("bit operation not supported for: " + xc);
	}

	// @WarnBoxedMath(false)
	public static boolean[] booleans(Object array) {
		return (boolean[]) array;
	}

	// @WarnBoxedMath(false)
	public static byte[] bytes(Object array) {
		return (byte[]) array;
	}

	static Category category(Object x) {
		Class<? extends Object> xc = x.getClass();

		if (xc == Integer.class)
			return Category.INTEGER;
		else if (xc == Double.class)
			return Category.FLOATING;
		else if (xc == Long.class)
			return Category.INTEGER;
		else if (xc == Float.class)
			return Category.FLOATING;
		else if (xc == Ratio.class)
			return Category.RATIO;
		else if (xc == BigDecimal.class)
			return Category.DECIMAL;
		else
			return Category.INTEGER;
	}

	// @WarnBoxedMath(false)
	public static char[] chars(Object array) {
		return (char[]) array;
	}

	public static long clearBit(long x, long n) {
		return x & ~(1L << n);
	}

	public static long clearBit(long x, Object y) {
		return clearBit(x, bitOpsCast(y));
	}

	public static long clearBit(Object x, long y) {
		return clearBit(bitOpsCast(x), y);
	}

	public static long clearBit(Object x, Object y) {
		return clearBit(bitOpsCast(x), bitOpsCast(y));
	}

	public static int compare(Number x, Number y) {
		Ops ops = ops(x).combine(ops(y));
		if (ops.lt(x, y))
			return -1;
		else if (ops.lt(y, x))
			return 1;
		return 0;
	}

	public static double dec(double x) {
		return x - 1;
	}

	public static long dec(long x) {
		if (x == Long.MIN_VALUE)
			return throwIntOverflow();
		return x - 1;
	}

	public static Number dec(Object x) {
		return ops(x).dec((Number) x);
	}

	public static double decP(double x) {
		return x - 1;
	}

	public static Number decP(long x) {
		if (x == Long.MIN_VALUE)
			return BIGINT_OPS.dec(x);
		return num(x - 1);
	}
	public static Number decP(Object x) {
		return ops(x).decP((Number) x);
	}
	public static Number divide(BigInteger n, BigInteger d) {
		if (d.equals(BigInteger.ZERO))
			throw new ArithmeticException("Divide by zero");
		BigInteger gcd = n.gcd(d);
		if (gcd.equals(BigInteger.ZERO))
			return BigInteger.ZERO;
		n = n.divide(gcd);
		d = d.divide(gcd);
		if (d.equals(BigInteger.ONE))
			return n;
		else if (d.equals(BigInteger.ONE.negate()))
			return n.negate();
		return new Ratio((d.signum() < 0 ? n.negate() : n), (d.signum() < 0 ? d.negate() : d));
	}
	public static double divide(double x, double y) {
		return x / y;
	}
	public static double divide(double x, long y) {
		return x / y;
	}

	public static double divide(double x, Object y) {
		return x / ((Number) y).doubleValue();
	};

	public static double divide(long x, double y) {
		return x / y;
	}
	
	/*
	// @WarnBoxedMath(false)
	static int hasheqFrom(Number x, Class<? extends Number> xc) {
		if (xc == Integer.class || xc == Short.class || xc == Byte.class
				|| (xc == BigInteger.class && lte(x, Long.MAX_VALUE) && gte(x, Long.MIN_VALUE))) {
			long lpart = x.longValue();
			return Hash.hashLong(lpart);
		}
		if (xc == BigDecimal.class) {
			// stripTrailingZeros() to make all numerically equal
			// BigDecimal values come out the same before calling
			// hashCode. Special check for 0 because
			// stripTrailingZeros() does not do anything to values
			// equal to 0 with different scales.
			if (isZero(x))
				return BigDecimal.ZERO.hashCode();
			else {
				BigDecimal tmp = ((BigDecimal) x).stripTrailingZeros();
				return tmp.hashCode();
			}
		}
		if (xc == Float.class && x.equals(-0.0f)) {
			return 0; // match 0.0f
		}
		return x.hashCode();
	}

	// @WarnBoxedMath(false)
	static int hasheq(Number x) {
		Class<? extends Number> xc = x.getClass();

		if (xc == Long.class) {
			long lpart = x.longValue();
			return Hash.hashLong(lpart);
		}
		if (xc == Double.class) {
			if (x.equals(-0.0))
				return 0; // match 0.0
			return x.hashCode();
		}
		return hasheqFrom(x, xc);
	}
	*/
	
	public static Number divide(long x, long y) {
		return divide((Number) x, (Number) y);
	}

	public static Number divide(long x, Object y) {
		return divide((Object) x, y);
	}
	
/*	// @WarnBoxedMath(false)
	public static float[] float_array(int size, Object init) {
		float[] ret = new float[size];
		if (init instanceof Number) {
			float f = ((Number) init).floatValue();
			for (int i = 0; i < ret.length; i++)
				ret[i] = f;
		} else {
			A.Seq<?> s = H.toSeq(init);
			for (int i = 0; i < size && s != null; i++, s = s.restMore())
				ret[i] = ((Number) s.first()).floatValue();
		}
		return ret;
	}

	// @WarnBoxedMath(false)
	public static float[] float_array(Object sizeOrSeq) {
		if (sizeOrSeq instanceof Number)
			return new float[((Number) sizeOrSeq).intValue()];
		else {
			A.Seq<?> s = H.toSeq(sizeOrSeq);
			int size = (int)H.count(s);
			float[] ret = new float[size];
			for (int i = 0; i < size && s != null; i++, s = s.restMore())
				ret[i] = ((Number) s.first()).floatValue();
			return ret;
		}
	}

	// @WarnBoxedMath(false)
	public static double[] double_array(int size, Object init) {
		double[] ret = new double[size];
		if (init instanceof Number) {
			double f = ((Number) init).doubleValue();
			for (int i = 0; i < ret.length; i++)
				ret[i] = f;
		} else {
			A.Seq<?> s = H.toSeq(init);
			for (int i = 0; i < size && s != null; i++, s = s.restMore())
				ret[i] = ((Number) s.first()).doubleValue();
		}
		return ret;
	}

	// @WarnBoxedMath(false)
	public static double[] double_array(Object sizeOrSeq) {
		if (sizeOrSeq instanceof Number)
			return new double[((Number) sizeOrSeq).intValue()];
		else {
			A.Seq<?> s = H.toSeq(sizeOrSeq);
			int size = (int)H.count(s);
			double[] ret = new double[size];
			for (int i = 0; i < size && s != null; i++, s = s.restMore())
				ret[i] = ((Number) s.first()).doubleValue();
			return ret;
		}
	}

	// @WarnBoxedMath(false)
	public static int[] int_array(int size, Object init) {
		int[] ret = new int[size];
		if (init instanceof Number) {
			int f = ((Number) init).intValue();
			for (int i = 0; i < ret.length; i++)
				ret[i] = f;
		} else {
			A.Seq<?> s = H.toSeq(init);
			for (int i = 0; i < size && s != null; i++, s = s.restMore())
				ret[i] = ((Number) s.first()).intValue();
		}
		return ret;
	}

	// @WarnBoxedMath(false)
	public static int[] int_array(Object sizeOrSeq) {
		if (sizeOrSeq instanceof Number)
			return new int[((Number) sizeOrSeq).intValue()];
		else {
			A.Seq<?> s = H.toSeq(sizeOrSeq);
			int size = (int)H.count(s);
			int[] ret = new int[size];
			for (int i = 0; i < size && s != null; i++, s = s.restMore())
				ret[i] = ((Number) s.first()).intValue();
			return ret;
		}
	}

	// @WarnBoxedMath(false)
	public static long[] long_array(int size, Object init) {
		long[] ret = new long[size];
		if (init instanceof Number) {
			long f = ((Number) init).longValue();
			for (int i = 0; i < ret.length; i++)
				ret[i] = f;
		} else {
			A.Seq<?> s = H.toSeq(init);
			for (int i = 0; i < size && s != null; i++, s = s.restMore())
				ret[i] = ((Number) s.first()).longValue();
		}
		return ret;
	}

	// @WarnBoxedMath(false)
	public static long[] long_array(Object sizeOrSeq) {
		if (sizeOrSeq instanceof Number)
			return new long[((Number) sizeOrSeq).intValue()];
		else {
			A.Seq<?> s = H.toSeq(sizeOrSeq);
			int size = (int)H.count(s);
			long[] ret = new long[size];
			for (int i = 0; i < size && s != null; i++, s = s.restMore())
				ret[i] = ((Number) s.first()).longValue();
			return ret;
		}
	}

	// @WarnBoxedMath(false)
	public static short[] short_array(int size, Object init) {
		short[] ret = new short[size];
		if (init instanceof Short) {
			short s = (Short) init;
			for (int i = 0; i < ret.length; i++)
				ret[i] = s;
		} else {
			A.Seq<?> s = H.toSeq(init);
			for (int i = 0; i < size && s != null; i++, s = s.restMore())
				ret[i] = ((Number) s.first()).shortValue();
		}
		return ret;
	}

	// @WarnBoxedMath(false)
	public static short[] short_array(Object sizeOrSeq) {
		if (sizeOrSeq instanceof Number)
			return new short[((Number) sizeOrSeq).intValue()];
		else {
			A.Seq<?> s = H.toSeq(sizeOrSeq);
			int size = (int)H.count(s);
			short[] ret = new short[size];
			for (int i = 0; i < size && s != null; i++, s = s.restMore())
				ret[i] = ((Number) s.first()).shortValue();
			return ret;
		}
	}

	// @WarnBoxedMath(false)
	public static char[] char_array(int size, Object init) {
		char[] ret = new char[size];
		if (init instanceof Character) {
			char c = (Character) init;
			for (int i = 0; i < ret.length; i++)
				ret[i] = c;
		} else {
			A.Seq<?> s = H.toSeq(init);
			for (int i = 0; i < size && s != null; i++, s = s.restMore())
				ret[i] = (Character) s.first();
		}
		return ret;
	}

	// @WarnBoxedMath(false)
	public static char[] char_array(Object sizeOrSeq) {
		if (sizeOrSeq instanceof Number)
			return new char[((Number) sizeOrSeq).intValue()];
		else {
			A.Seq<?> s = H.toSeq(sizeOrSeq);
			int size = (int)H.count(s);
			char[] ret = new char[size];
			for (int i = 0; i < size && s != null; i++, s = s.restMore())
				ret[i] = (Character) s.first();
			return ret;
		}
	}

	// @WarnBoxedMath(false)
	public static byte[] byte_array(int size, Object init) {
		byte[] ret = new byte[size];
		if (init instanceof Byte) {
			byte b = (Byte) init;
			for (int i = 0; i < ret.length; i++)
				ret[i] = b;
		} else {
			A.Seq<?> s = H.toSeq(init);
			for (int i = 0; i < size && s != null; i++, s = s.restMore())
				ret[i] = ((Number) s.first()).byteValue();
		}
		return ret;
	}

	// @WarnBoxedMath(false)
	public static byte[] byte_array(Object sizeOrSeq) {
		if (sizeOrSeq instanceof Number)
			return new byte[((Number) sizeOrSeq).intValue()];
		else {
			A.Seq<?> s = H.toSeq(sizeOrSeq);
			int size = (int)H.count(s);
			byte[] ret = new byte[size];
			for (int i = 0; i < size && s != null; i++, s = s.restMore())
				ret[i] = ((Number) s.first()).byteValue();
			return ret;
		}
	}

	// @WarnBoxedMath(false)
	public static boolean[] boolean_array(int size, Object init) {
		boolean[] ret = new boolean[size];
		if (init instanceof Boolean) {
			boolean b = (Boolean) init;
			for (int i = 0; i < ret.length; i++)
				ret[i] = b;
		} else {
			A.Seq<?> s = H.toSeq(init);
			for (int i = 0; i < size && s != null; i++, s = s.restMore())
				ret[i] = (Boolean) s.first();
		}
		return ret;
	}

	// @WarnBoxedMath(false)
	public static boolean[] boolean_array(Object sizeOrSeq) {
		if (sizeOrSeq instanceof Number)
			return new boolean[((Number) sizeOrSeq).intValue()];
		else {
			A.Seq<?> s = H.toSeq(sizeOrSeq);
			int size = (int)H.count(s);
			boolean[] ret = new boolean[size];
			for (int i = 0; i < size && s != null; i++, s = s.restMore())
				ret[i] = (Boolean) s.first();
			return ret;
		}
	}
	*/

	public static double divide(Object x, double y) {
		return ((Number) x).doubleValue() / y;
	}

	public static Number divide(Object x, long y) {
		return divide(x, (Object) y);
	}

	public static Number divide(Object x, Object y) {
		if (isNaN(x)) {
			return (Number) x;
		} else if (isNaN(y)) {
			return (Number) y;
		}
		Ops yops = ops(y);
		if (yops.isZero((Number) y))
			throw new ArithmeticException("Divide by zero");
		return ops(x).combine(yops).divide((Number) x, (Number) y);
	}

	// @WarnBoxedMath(false)
	public static double[] doubles(Object array) {
		return (double[]) array;
	}

	public static boolean eq(double x, double y) {
		return x == y;
	}

	public static boolean eq(double x, long y) {
		return x == y;
	}

	public static boolean eq(double x, Object y) {
		return x == ((Number) y).doubleValue();
	}

	public static boolean eq(long x, double y) {
		return x == y;
	}

	public static boolean eq(long x, long y) {
		return x == y;
	}

	public static boolean eq(long x, Object y) {
		return eq((Object) x, y);
	}

	public static boolean eq(Number x, Number y) {
		return ops(x).combine(ops(y)).eq(x, y);
	}

	public static boolean eq(Object x, double y) {
		return ((Number) x).doubleValue() == y;
	}

	public static boolean eq(Object x, long y) {
		return eq(x, (Object) y);
	}

	public static boolean eq(Object x, Object y) {
		return eq((Number) x, (Number) y);
	}

	public static boolean equal(Number x, Number y) {
		return category(x) == category(y) && ops(x).combine(ops(y)).eq(x, y);
	}

	public static long flipBit(long x, long n) {
		return x ^ (1L << n);
	}

	public static long flipBit(long x, Object y) {
		return flipBit(x, bitOpsCast(y));
	}

	public static long flipBit(Object x, long y) {
		return flipBit(bitOpsCast(x), y);
	}

	public static long flipBit(Object x, Object y) {
		return flipBit(bitOpsCast(x), bitOpsCast(y));
	}

	// @WarnBoxedMath(false)
	public static float[] floats(Object array) {
		return (float[]) array;
	}

	public static boolean gt(double x, double y) {
		return x > y;
	}

	public static boolean gt(double x, long y) {
		return x > y;
	}

	public static boolean gt(double x, Object y) {
		return x > ((Number) y).doubleValue();
	}

	public static boolean gt(long x, double y) {
		return x > y;
	}

	public static boolean gt(long x, long y) {
		return x > y;
	}

	public static boolean gt(long x, Object y) {
		return gt((Object) x, y);
	}

	public static boolean gt(Object x, double y) {
		return ((Number) x).doubleValue() > y;
	}

	public static boolean gt(Object x, long y) {
		return gt(x, (Object) y);
	}

	public static boolean gt(Object x, Object y) {
		return ops(x).combine(ops(y)).lt((Number) y, (Number) x);
	}

	public static boolean gte(double x, double y) {
		return x >= y;
	}

	public static boolean gte(double x, long y) {
		return x >= y;
	}

	public static boolean gte(double x, Object y) {
		return x >= ((Number) y).doubleValue();
	}

	public static boolean gte(long x, double y) {
		return x >= y;
	}

//public static Number num(int x){
//	return Integer.valueOf(x);
//}

	public static boolean gte(long x, long y) {
		return x >= y;
	}

	public static boolean gte(long x, Object y) {
		return gte((Object) x, y);
	}

	public static boolean gte(Object x, double y) {
		return ((Number) x).doubleValue() >= y;
	}

	public static boolean gte(Object x, long y) {
		return gte(x, (Object) y);
	}

	public static boolean gte(Object x, Object y) {
		return ops(x).combine(ops(y)).gte((Number) x, (Number) y);
	}

	public static double inc(double x) {
		return x + 1;
	}

//public static int add(int x, int y){
//	int ret = x + y;
//	if ((ret ^ x) < 0 && (ret ^ y) < 0)
//		return throwIntOverflow();
//	return ret;
//}

//public static int not(int x){
//	return ~x;
//}

	public static long inc(long x) {
		if (x == Long.MAX_VALUE)
			return throwIntOverflow();
		return x + 1;
	}

	public static Number inc(Object x) {
		return ops(x).inc((Number) x);
	}

	public static double incP(double x) {
		return x + 1;
	}

	public static Number incP(long x) {
		if (x == Long.MAX_VALUE)
			return BIGINT_OPS.inc(x);
		return num(x + 1);
	}

	public static Number incP(Object x) {
		return ops(x).incP((Number) x);
	}

	// @WarnBoxedMath(false)
	public static int[] ints(Object array) {
		return (int[]) array;
	}

//public static int or(int x, int y){
//	return x | y;
//}

	static boolean isNaN(Object x) {
		return (x instanceof Double) && ((Double) x).isNaN() || (x instanceof Float) && ((Float) x).isNaN();
	}

	public static boolean isNeg(double x) {
		return x < 0;
	}

	public static boolean isNeg(long x) {
		return x < 0;
	}

	public static boolean isNeg(Object x) {
		return ops(x).isNeg((Number) x);
	}

//public static int xor(int x, int y){
//	return x ^ y;
//}

	public static boolean isPos(double x) {
		return x > 0;
	}

	public static boolean isPos(long x) {
		return x > 0;
	}

	public static boolean isPos(Object x) {
		return ops(x).isPos((Number) x);
	}

	public static boolean isZero(double x) {
		return x == 0;
	}

	public static boolean isZero(long x) {
		return x == 0;
	}

	public static boolean isZero(Object x) {
		return ops(x).isZero((Number) x);
	}

	// @WarnBoxedMath(false)
	public static long[] longs(Object array) {
		return (long[]) array;
	}

	public static boolean lt(double x, double y) {
		return x < y;
	}

	public static boolean lt(double x, long y) {
		return x < y;
	}

	public static boolean lt(double x, Object y) {
		return x < ((Number) y).doubleValue();
	}

	public static boolean lt(long x, double y) {
		return x < y;
	}

	public static boolean lt(long x, long y) {
		return x < y;
	}

	public static boolean lt(long x, Object y) {
		return lt((Object) x, y);
	}

	public static boolean lt(Object x, double y) {
		return ((Number) x).doubleValue() < y;
	}

	public static boolean lt(Object x, long y) {
		return lt(x, (Object) y);
	}

	public static boolean lt(Object x, Object y) {
		return ops(x).combine(ops(y)).lt((Number) x, (Number) y);
	}

	public static boolean lte(double x, double y) {
		return x <= y;
	}

	public static boolean lte(double x, long y) {
		return x <= y;
	}

	public static boolean lte(double x, Object y) {
		return x <= ((Number) y).doubleValue();
	}

	public static boolean lte(long x, double y) {
		return x <= y;
	}

	public static boolean lte(long x, long y) {
		return x <= y;
	}

	public static boolean lte(long x, Object y) {
		return lte((Object) x, y);
	}

	public static boolean lte(Object x, double y) {
		return ((Number) x).doubleValue() <= y;
	}

	public static boolean lte(Object x, long y) {
		return lte(x, (Object) y);
	}

//public static int minus(int x, int y){
//	int ret = x - y;
//	if (((ret ^ x) < 0 && (ret ^ ~y) < 0))
//		return throwIntOverflow();
//	return ret;
//}

//public static int minus(int x){
//	if(x == Integer.MIN_VALUE)
//		return throwIntOverflow();
//	return -x;
//}

//public static int inc(int x){
//	if(x == Integer.MAX_VALUE)
//		return throwIntOverflow();
//	return x + 1;
//}

//public static int dec(int x){
//	if(x == Integer.MIN_VALUE)
//		return throwIntOverflow();
//	return x - 1;
//}

//public static int multiply(int x, int y){
//	int ret = x * y;
//	if (y != 0 && ret/y != x)
//		return throwIntOverflow();
//	return ret;
//}

	public static boolean lte(Object x, Object y) {
		return ops(x).combine(ops(y)).lte((Number) x, (Number) y);
	}

	public static double minus(double x) {
		return -x;
	}

//public static boolean eq(int x, int y){
//	return x == y;
//}

//public static boolean lt(int x, int y){
//	return x < y;
//}

//public static boolean lte(int x, int y){
//	return x <= y;
//}

//public static boolean gt(int x, int y){
//	return x > y;
//}

//public static boolean gte(int x, int y){
//	return x >= y;
//}

//public static boolean isPos(int x){
//	return x > 0;
//}

//public static boolean isNeg(int x){
//	return x < 0;
//}

//public static boolean isZero(int x){
//	return x == 0;
//}

	public static double minus(double x, double y) {
		return x - y;
	}

	public static double minus(double x, long y) {
		return x - y;
	}

	public static double minus(double x, Object y) {
		return minus(x, ((Number) y).doubleValue());
	}

	public static long minus(long x) {
		if (x == Long.MIN_VALUE)
			return throwIntOverflow();
		return -x;
	}

	public static double minus(long x, double y) {
		return x - y;
	}

	public static long minus(long x, long y) {
		long ret = x - y;
		if (((ret ^ x) < 0 && (ret ^ ~y) < 0))
			return throwIntOverflow();
		return ret;
	}

	public static Number minus(long x, Object y) {
		return minus((Object) x, y);
	}

	public static Number minus(Object x) {
		return ops(x).negate((Number) x);
	}

	public static double minus(Object x, double y) {
		return minus(((Number) x).doubleValue(), y);
	}

	public static Number minus(Object x, long y) {
		return minus(x, (Object) y);
	}

	public static Number minus(Object x, Object y) {
		Ops yops = ops(y);
		return ops(x).combine(yops).add((Number) x, yops.negate((Number) y));
	}

	public static double minusP(double x) {
		return -x;
	}

	public static double minusP(double x, double y) {
		return x - y;
	}

	public static double minusP(double x, long y) {
		return x - y;
	}

	public static double minusP(double x, Object y) {
		return minus(x, ((Number) y).doubleValue());
	}

	public static Number minusP(long x) {
		if (x == Long.MIN_VALUE)
			return BigInteger.valueOf(x).negate();
		return num(-x);
	}

	public static double minusP(long x, double y) {
		return x - y;
	}

	public static Number minusP(long x, long y) {
		long ret = x - y;
		if (((ret ^ x) < 0 && (ret ^ ~y) < 0))
			return minusP((Number) x, (Number) y);
		return num(ret);
	}

	public static Number minusP(long x, Object y) {
		return minusP((Object) x, y);
	}

	public static Number minusP(Object x) {
		return ops(x).negateP((Number) x);
	}

	public static double minusP(Object x, double y) {
		return minus(((Number) x).doubleValue(), y);
	}

	public static Number minusP(Object x, long y) {
		return minusP(x, (Object) y);
	}

	public static Number minusP(Object x, Object y) {
		Ops yops = ops(y);
		Number negativeY = yops.negateP((Number) y);
		Ops negativeYOps = ops(negativeY);
		return ops(x).combine(negativeYOps).addP((Number) x, negativeY);
	}

	public static double multiply(double x, double y) {
		return x * y;
	}

	public static double multiply(double x, long y) {
		return x * y;
	}

	public static double multiply(double x, Object y) {
		return multiply(x, ((Number) y).doubleValue());
	}

	public static double multiply(long x, double y) {
		return x * y;
	}

	public static long multiply(long x, long y) {
		if (x == Long.MIN_VALUE && y < 0)
			return throwIntOverflow();
		long ret = x * y;
		if (y != 0 && ret / y != x)
			return throwIntOverflow();
		return ret;
	}

	public static Number multiply(long x, Object y) {
		return multiply((Object) x, y);
	}

	public static double multiply(Object x, double y) {
		return multiply(((Number) x).doubleValue(), y);
	}

	public static Number multiply(Object x, long y) {
		return multiply(x, (Object) y);
	}

	public static Number multiply(Object x, Object y) {
		return ops(x).combine(ops(y)).multiply((Number) x, (Number) y);
	}

	public static double multiplyP(double x, double y) {
		return x * y;
	}

	public static double multiplyP(double x, long y) {
		return x * y;
	}

	public static double multiplyP(double x, Object y) {
		return multiplyP(x, ((Number) y).doubleValue());
	}

	public static double multiplyP(long x, double y) {
		return x * y;
	}

	public static Number multiplyP(long x, long y) {
		if (x == Long.MIN_VALUE && y < 0)
			return multiplyP((Number) x, (Number) y);
		long ret = x * y;
		if (y != 0 && ret / y != x)
			return multiplyP((Number) x, (Number) y);
		return num(ret);
	}

	public static Number multiplyP(long x, Object y) {
		return multiplyP((Object) x, y);
	}

	public static double multiplyP(Object x, double y) {
		return multiplyP(((Number) x).doubleValue(), y);
	}

	public static Number multiplyP(Object x, long y) {
		return multiplyP(x, (Object) y);
	}

	public static Number multiplyP(Object x, Object y) {
		return ops(x).combine(ops(y)).multiplyP((Number) x, (Number) y);
	}

	public static long not(long x) {
		return ~x;
	}
//public static int and(int x, int y){
//	return x & y;
//}

	public static long not(Object x) {
		return not(bitOpsCast(x));
	}

	public static Number num(double x) {
		return Double.valueOf(x);
	}

	public static Number num(float x) {
		return Float.valueOf(x);
	}

	public static Number num(long x) {
		return Long.valueOf(x);
	}

	public static Number num(Object x) {
		return (Number) x;
	}

	static Ops ops(Object x) {
		Class<? extends Object> xc = x.getClass();

		if (xc == Long.class)
			return LONG_OPS;
		else if (xc == Double.class)
			return DOUBLE_OPS;
		else if (xc == Integer.class)
			return LONG_OPS;
		else if (xc == Float.class)
			return DOUBLE_OPS;
		else if (xc == BigInteger.class)
			return BIGINT_OPS;
		else if (xc == BigInteger.class)
			return BIGINT_OPS;
		else if (xc == Ratio.class)
			return RATIO_OPS;
		else if (xc == BigDecimal.class)
			return BIGDECIMAL_OPS;
		else
			return LONG_OPS;
	}

	public static long or(long x, long y) {
		return x | y;
	}

	public static long or(long x, Object y) {
		return or(x, bitOpsCast(y));
	}

	public static long or(Object x, long y) {
		return or(bitOpsCast(x), y);
	}

	public static long or(Object x, Object y) {
		return or(bitOpsCast(x), bitOpsCast(y));
	}

	public static double quotient(double n, double d) {
		if (d == 0)
			throw new ArithmeticException("Divide by zero");

		double q = n / d;
		if (q <= Long.MAX_VALUE && q >= Long.MIN_VALUE) {
			return (long) q;
		} else { // bigint quotient
			return new BigDecimal(q).toBigInteger().doubleValue();
		}
	}

	public static double quotient(double x, long y) {
		return quotient(x, (double) y);
	}

	public static Number quotient(double x, Object y) {
		return quotient((Object) x, y);
	}

	public static double quotient(long x, double y) {
		return quotient((double) x, y);
	}

	public static long quotient(long x, long y) {
		return x / y;
	}

	public static Number quotient(long x, Object y) {
		return quotient((Object) x, y);
	}

	public static Number quotient(Object x, double y) {
		return quotient(x, (Object) y);
	}

	public static Number quotient(Object x, long y) {
		return quotient(x, (Object) y);
	}

	public static Number quotient(Object x, Object y) {
		Ops yops = ops(y);
		if (yops.isZero((Number) y))
			throw new ArithmeticException("Divide by zero");
		return ops(x).combine(yops).quotient((Number) x, (Number) y);
	}

	// @WarnBoxedMath(false)
	public static Number rationalize(Number x) {
		if (x instanceof Float || x instanceof Double)
			return rationalize(BigDecimal.valueOf(x.doubleValue()));
		else if (x instanceof BigDecimal) {
			BigDecimal bx = (BigDecimal) x;
			BigInteger bv = bx.unscaledValue();
			int scale = bx.scale();
			if (scale < 0)
				return bv.multiply(BigInteger.TEN.pow(-scale));
			else
				return divide(bv, BigInteger.TEN.pow(scale));
		}
		return x;
	}

	public static double remainder(double n, double d) {
		if (d == 0)
			throw new ArithmeticException("Divide by zero");

		double q = n / d;
		if (q <= Long.MAX_VALUE && q >= Long.MIN_VALUE) {
			return (n - ((long) q) * d);
		} else { // bigint quotient
			Number bq = new BigDecimal(q).toBigInteger();
			return (n - bq.doubleValue() * d);
		}
	}

	public static double remainder(double x, long y) {
		return remainder(x, (double) y);
	}

	public static Number remainder(double x, Object y) {
		return remainder((Object) x, y);
	}

	public static double remainder(long x, double y) {
		return remainder((double) x, y);
	}

	public static long remainder(long x, long y) {
		return x % y;
	}

	public static Number remainder(long x, Object y) {
		return remainder((Object) x, y);
	}

	public static Number remainder(Object x, double y) {
		return remainder(x, (Object) y);
	}

	public static Number remainder(Object x, long y) {
		return remainder(x, (Object) y);
	}

	public static Number remainder(Object x, Object y) {
		Ops yops = ops(y);
		if (yops.isZero((Number) y))
			throw new ArithmeticException("Divide by zero");
		return ops(x).combine(yops).remainder((Number) x, (Number) y);
	}

	/*
	 * public static class F{ public static float add(float x, float y){ return x +
	 * y; }
	 * 
	 * public static float subtract(float x, float y){ return x - y; }
	 * 
	 * public static float negate(float x){ return -x; }
	 * 
	 * public static float inc(float x){ return x + 1; }
	 * 
	 * public static float dec(float x){ return x - 1; }
	 * 
	 * public static float multiply(float x, float y){ return x * y; }
	 * 
	 * public static float divide(float x, float y){ return x / y; }
	 * 
	 * public static boolean eq(float x, float y){ return x == y; }
	 * 
	 * public static boolean lt(float x, float y){ return x < y; }
	 * 
	 * public static boolean lte(float x, float y){ return x <= y; }
	 * 
	 * public static boolean gt(float x, float y){ return x > y; }
	 * 
	 * public static boolean gte(float x, float y){ return x >= y; }
	 * 
	 * public static boolean pos(float x){ return x > 0; }
	 * 
	 * public static boolean neg(float x){ return x < 0; }
	 * 
	 * public static boolean zero(float x){ return x == 0; }
	 * 
	 * public static float aget(float[] xs, int i){ return xs[i]; }
	 * 
	 * public static float aset(float[] xs, int i, float v){ xs[i] = v; return v; }
	 * 
	 * public static int alength(float[] xs){ return xs.length; }
	 * 
	 * public static float[] aclone(float[] xs){ return xs.clone(); }
	 * 
	 * public static float[] vec(int size, Object init){ float[] ret = new
	 * float[size]; if(init instanceof Number) { float f = ((Number)
	 * init).floatValue(); for(int i = 0; i < ret.length; i++) ret[i] = f; } else {
	 * A.Seq<?> s = Coll.toSeq(init); for(int i = 0; i < size && s != null; i++, s =
	 * s.rest()) ret[i] = ((Number) s.first()).floatValue(); } return ret; }
	 * 
	 * public static float[] vec(Object sizeOrSeq){ if(sizeOrSeq instanceof Number)
	 * return new float[((Number) sizeOrSeq).intValue()]; else { A.Seq<?> s =
	 * Coll.toSeq(sizeOrSeq); int size = s.count(); float[] ret = new float[size];
	 * for(int i = 0; i < size && s != null; i++, s = s.rest()) ret[i] = ((Number)
	 * s.first()).intValue(); return ret; } }
	 * 
	 * 
	 * public static float[] vsadd(float[] x, float y){ final float[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] += y; return xs; }
	 * 
	 * public static float[] vssub(float[] x, float y){ final float[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] -= y; return xs; }
	 * 
	 * public static float[] vsdiv(float[] x, float y){ final float[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] /= y; return xs; }
	 * 
	 * public static float[] vsmul(float[] x, float y){ final float[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] *= y; return xs; }
	 * 
	 * public static float[] svdiv(float y, float[] x){ final float[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = y / xs[i]; return xs; }
	 * 
	 * public static float[] vsmuladd(float[] x, float y, float[] zs){ final float[]
	 * xs = x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = xs[i] * y + zs[i];
	 * return xs; }
	 * 
	 * public static float[] vsmulsub(float[] x, float y, float[] zs){ final float[]
	 * xs = x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = xs[i] * y - zs[i];
	 * return xs; }
	 * 
	 * public static float[] vsmulsadd(float[] x, float y, float z){ final float[]
	 * xs = x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = xs[i] * y + z;
	 * return xs; }
	 * 
	 * public static float[] vsmulssub(float[] x, float y, float z){ final float[]
	 * xs = x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = xs[i] * y - z;
	 * return xs; }
	 * 
	 * public static float[] vabs(float[] x){ final float[] xs = x.clone(); for(int
	 * i = 0; i < xs.length; i++) xs[i] = Math.abs(xs[i]); return xs; }
	 * 
	 * public static float[] vnegabs(float[] x){ final float[] xs = x.clone();
	 * for(int i = 0; i < xs.length; i++) xs[i] = -Math.abs(xs[i]); return xs; }
	 * 
	 * public static float[] vneg(float[] x){ final float[] xs = x.clone(); for(int
	 * i = 0; i < xs.length; i++) xs[i] = -xs[i]; return xs; }
	 * 
	 * public static float[] vsqr(float[] x){ final float[] xs = x.clone(); for(int
	 * i = 0; i < xs.length; i++) xs[i] *= xs[i]; return xs; }
	 * 
	 * public static float[] vsignedsqr(float[] x){ final float[] xs = x.clone();
	 * for(int i = 0; i < xs.length; i++) xs[i] *= Math.abs(xs[i]); return xs; }
	 * 
	 * public static float[] vclip(float[] x, float low, float high){ final float[]
	 * xs = x.clone(); for(int i = 0; i < xs.length; i++) { if(xs[i] < low) xs[i] =
	 * low; else if(xs[i] > high) xs[i] = high; } return xs; }
	 * 
	 * public static IPersistentVector vclipcounts(float[] x, float low, float
	 * high){ final float[] xs = x.clone(); int lowc = 0; int highc = 0;
	 * 
	 * for(int i = 0; i < xs.length; i++) { if(xs[i] < low) { ++lowc; xs[i] = low; }
	 * else if(xs[i] > high) { ++highc; xs[i] = high; } } return Ut.vector(xs, lowc,
	 * highc); }
	 * 
	 * public static float[] vthresh(float[] x, float thresh, float otherwise){
	 * final float[] xs = x.clone(); for(int i = 0; i < xs.length; i++) { if(xs[i] <
	 * thresh) xs[i] = otherwise; } return xs; }
	 * 
	 * public static float[] vreverse(float[] x){ final float[] xs = x.clone();
	 * for(int i = 0; i < xs.length; i++) xs[i] = xs[xs.length - i - 1]; return xs;
	 * }
	 * 
	 * public static float[] vrunningsum(float[] x){ final float[] xs = x.clone();
	 * for(int i = 1; i < xs.length; i++) xs[i] = xs[i - 1] + xs[i]; return xs; }
	 * 
	 * public static float[] vsort(float[] x){ final float[] xs = x.clone();
	 * Arrays.sort(xs); return xs; }
	 * 
	 * public static float vdot(float[] xs, float[] ys){ float ret = 0; for(int i =
	 * 0; i < xs.length; i++) ret += xs[i] * ys[i]; return ret; }
	 * 
	 * public static float vmax(float[] xs){ if(xs.length == 0) return 0; float ret
	 * = xs[0]; for(int i = 0; i < xs.length; i++) ret = Math.max(ret, xs[i]);
	 * return ret; }
	 * 
	 * public static float vmin(float[] xs){ if(xs.length == 0) return 0; float ret
	 * = xs[0]; for(int i = 0; i < xs.length; i++) ret = Math.min(ret, xs[i]);
	 * return ret; }
	 * 
	 * public static float vmean(float[] xs){ if(xs.length == 0) return 0; return
	 * vsum(xs) / xs.length; }
	 * 
	 * public static double vrms(float[] xs){ if(xs.length == 0) return 0; float ret
	 * = 0; for(int i = 0; i < xs.length; i++) ret += xs[i] * xs[i]; return
	 * Math.sqrt(ret / xs.length); }
	 * 
	 * public static float vsum(float[] xs){ float ret = 0; for(int i = 0; i <
	 * xs.length; i++) ret += xs[i]; return ret; }
	 * 
	 * public static boolean veq(float[] xs, float[] ys){ return Arrays.equals(xs,
	 * ys); }
	 * 
	 * public static float[] vadd(float[] x, float[] ys){ final float[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] += ys[i]; return xs; }
	 * 
	 * public static float[] vsub(float[] x, float[] ys){ final float[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] -= ys[i]; return xs; }
	 * 
	 * public static float[] vaddmul(float[] x, float[] ys, float[] zs){ final
	 * float[] xs = x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = (xs[i] +
	 * ys[i]) * zs[i]; return xs; }
	 * 
	 * public static float[] vsubmul(float[] x, float[] ys, float[] zs){ final
	 * float[] xs = x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = (xs[i] -
	 * ys[i]) * zs[i]; return xs; }
	 * 
	 * public static float[] vaddsmul(float[] x, float[] ys, float z){ final float[]
	 * xs = x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = (xs[i] + ys[i]) *
	 * z; return xs; }
	 * 
	 * public static float[] vsubsmul(float[] x, float[] ys, float z){ final float[]
	 * xs = x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = (xs[i] - ys[i]) *
	 * z; return xs; }
	 * 
	 * public static float[] vmulsadd(float[] x, float[] ys, float z){ final float[]
	 * xs = x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = (xs[i] * ys[i]) +
	 * z; return xs; }
	 * 
	 * public static float[] vdiv(float[] x, float[] ys){ final float[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] /= ys[i]; return xs; }
	 * 
	 * public static float[] vmul(float[] x, float[] ys){ final float[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] *= ys[i]; return xs; }
	 * 
	 * public static float[] vmuladd(float[] x, float[] ys, float[] zs){ final
	 * float[] xs = x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = (xs[i] *
	 * ys[i]) + zs[i]; return xs; }
	 * 
	 * public static float[] vmulsub(float[] x, float[] ys, float[] zs){ final
	 * float[] xs = x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = (xs[i] *
	 * ys[i]) - zs[i]; return xs; }
	 * 
	 * public static float[] vmax(float[] x, float[] ys){ final float[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = Math.max(xs[i], ys[i]);
	 * return xs; }
	 * 
	 * public static float[] vmin(float[] x, float[] ys){ final float[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = Math.min(xs[i], ys[i]);
	 * return xs; }
	 * 
	 * public static float[] vmap(IFn fn, float[] x) { float[] xs = x.clone();
	 * for(int i = 0; i < xs.length; i++) xs[i] = ((Number)
	 * fn.invoke(xs[i])).floatValue(); return xs; }
	 * 
	 * public static float[] vmap(IFn fn, float[] x, float[] ys) { float[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = ((Number)
	 * fn.invoke(xs[i], ys[i])).floatValue(); return xs; }
	 * 
	 * }
	 * 
	 * public static class D{ public static double add(double x, double y){ return x
	 * + y; }
	 * 
	 * public static double subtract(double x, double y){ return x - y; }
	 * 
	 * public static double negate(double x){ return -x; }
	 * 
	 * public static double inc(double x){ return x + 1; }
	 * 
	 * public static double dec(double x){ return x - 1; }
	 * 
	 * public static double multiply(double x, double y){ return x * y; }
	 * 
	 * public static double divide(double x, double y){ return x / y; }
	 * 
	 * public static boolean eq(double x, double y){ return x == y; }
	 * 
	 * public static boolean lt(double x, double y){ return x < y; }
	 * 
	 * public static boolean lte(double x, double y){ return x <= y; }
	 * 
	 * public static boolean gt(double x, double y){ return x > y; }
	 * 
	 * public static boolean gte(double x, double y){ return x >= y; }
	 * 
	 * public static boolean pos(double x){ return x > 0; }
	 * 
	 * public static boolean neg(double x){ return x < 0; }
	 * 
	 * public static boolean zero(double x){ return x == 0; }
	 * 
	 * public static double aget(double[] xs, int i){ return xs[i]; }
	 * 
	 * public static double aset(double[] xs, int i, double v){ xs[i] = v; return v;
	 * }
	 * 
	 * public static int alength(double[] xs){ return xs.length; }
	 * 
	 * public static double[] aclone(double[] xs){ return xs.clone(); }
	 * 
	 * public static double[] vec(int size, Object init){ double[] ret = new
	 * double[size]; if(init instanceof Number) { double f = ((Number)
	 * init).doubleValue(); for(int i = 0; i < ret.length; i++) ret[i] = f; } else {
	 * A.Seq<?> s = Coll.toSeq(init); for(int i = 0; i < size && s != null; i++, s =
	 * s.rest()) ret[i] = ((Number) s.first()).doubleValue(); } return ret; }
	 * 
	 * public static double[] vec(Object sizeOrSeq){ if(sizeOrSeq instanceof Number)
	 * return new double[((Number) sizeOrSeq).intValue()]; else { A.Seq<?> s =
	 * Coll.toSeq(sizeOrSeq); int size = s.count(); double[] ret = new double[size];
	 * for(int i = 0; i < size && s != null; i++, s = s.rest()) ret[i] = ((Number)
	 * s.first()).intValue(); return ret; } }
	 * 
	 * public static double[] vsadd(double[] x, double y){ final double[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] += y; return xs; }
	 * 
	 * public static double[] vssub(double[] x, double y){ final double[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] -= y; return xs; }
	 * 
	 * public static double[] vsdiv(double[] x, double y){ final double[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] /= y; return xs; }
	 * 
	 * public static double[] vsmul(double[] x, double y){ final double[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] *= y; return xs; }
	 * 
	 * public static double[] svdiv(double y, double[] x){ final double[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = y / xs[i]; return xs; }
	 * 
	 * public static double[] vsmuladd(double[] x, double y, double[] zs){ final
	 * double[] xs = x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = xs[i] * y
	 * + zs[i]; return xs; }
	 * 
	 * public static double[] vsmulsub(double[] x, double y, double[] zs){ final
	 * double[] xs = x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = xs[i] * y
	 * - zs[i]; return xs; }
	 * 
	 * public static double[] vsmulsadd(double[] x, double y, double z){ final
	 * double[] xs = x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = xs[i] * y
	 * + z; return xs; }
	 * 
	 * public static double[] vsmulssub(double[] x, double y, double z){ final
	 * double[] xs = x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = xs[i] * y
	 * - z; return xs; }
	 * 
	 * public static double[] vabs(double[] x){ final double[] xs = x.clone();
	 * for(int i = 0; i < xs.length; i++) xs[i] = Math.abs(xs[i]); return xs; }
	 * 
	 * public static double[] vnegabs(double[] x){ final double[] xs = x.clone();
	 * for(int i = 0; i < xs.length; i++) xs[i] = -Math.abs(xs[i]); return xs; }
	 * 
	 * public static double[] vneg(double[] x){ final double[] xs = x.clone();
	 * for(int i = 0; i < xs.length; i++) xs[i] = -xs[i]; return xs; }
	 * 
	 * public static double[] vsqr(double[] x){ final double[] xs = x.clone();
	 * for(int i = 0; i < xs.length; i++) xs[i] *= xs[i]; return xs; }
	 * 
	 * public static double[] vsignedsqr(double[] x){ final double[] xs = x.clone();
	 * for(int i = 0; i < xs.length; i++) xs[i] *= Math.abs(xs[i]); return xs; }
	 * 
	 * public static double[] vclip(double[] x, double low, double high){ final
	 * double[] xs = x.clone(); for(int i = 0; i < xs.length; i++) { if(xs[i] < low)
	 * xs[i] = low; else if(xs[i] > high) xs[i] = high; } return xs; }
	 * 
	 * public static IPersistentVector vclipcounts(double[] x, double low, double
	 * high){ final double[] xs = x.clone(); int lowc = 0; int highc = 0;
	 * 
	 * for(int i = 0; i < xs.length; i++) { if(xs[i] < low) { ++lowc; xs[i] = low; }
	 * else if(xs[i] > high) { ++highc; xs[i] = high; } } return Ut.vector(xs, lowc,
	 * highc); }
	 * 
	 * public static double[] vthresh(double[] x, double thresh, double otherwise){
	 * final double[] xs = x.clone(); for(int i = 0; i < xs.length; i++) { if(xs[i]
	 * < thresh) xs[i] = otherwise; } return xs; }
	 * 
	 * public static double[] vreverse(double[] x){ final double[] xs = x.clone();
	 * for(int i = 0; i < xs.length; i++) xs[i] = xs[xs.length - i - 1]; return xs;
	 * }
	 * 
	 * public static double[] vrunningsum(double[] x){ final double[] xs =
	 * x.clone(); for(int i = 1; i < xs.length; i++) xs[i] = xs[i - 1] + xs[i];
	 * return xs; }
	 * 
	 * public static double[] vsort(double[] x){ final double[] xs = x.clone();
	 * Arrays.sort(xs); return xs; }
	 * 
	 * public static double vdot(double[] xs, double[] ys){ double ret = 0; for(int
	 * i = 0; i < xs.length; i++) ret += xs[i] * ys[i]; return ret; }
	 * 
	 * public static double vmax(double[] xs){ if(xs.length == 0) return 0; double
	 * ret = xs[0]; for(int i = 0; i < xs.length; i++) ret = Math.max(ret, xs[i]);
	 * return ret; }
	 * 
	 * public static double vmin(double[] xs){ if(xs.length == 0) return 0; double
	 * ret = xs[0]; for(int i = 0; i < xs.length; i++) ret = Math.min(ret, xs[i]);
	 * return ret; }
	 * 
	 * public static double vmean(double[] xs){ if(xs.length == 0) return 0; return
	 * vsum(xs) / xs.length; }
	 * 
	 * public static double vrms(double[] xs){ if(xs.length == 0) return 0; double
	 * ret = 0; for(int i = 0; i < xs.length; i++) ret += xs[i] * xs[i]; return
	 * Math.sqrt(ret / xs.length); }
	 * 
	 * public static double vsum(double[] xs){ double ret = 0; for(int i = 0; i <
	 * xs.length; i++) ret += xs[i]; return ret; }
	 * 
	 * public static boolean veq(double[] xs, double[] ys){ return Arrays.equals(xs,
	 * ys); }
	 * 
	 * public static double[] vadd(double[] x, double[] ys){ final double[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] += ys[i]; return xs; }
	 * 
	 * public static double[] vsub(double[] x, double[] ys){ final double[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] -= ys[i]; return xs; }
	 * 
	 * public static double[] vaddmul(double[] x, double[] ys, double[] zs){ final
	 * double[] xs = x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = (xs[i] +
	 * ys[i]) * zs[i]; return xs; }
	 * 
	 * public static double[] vsubmul(double[] x, double[] ys, double[] zs){ final
	 * double[] xs = x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = (xs[i] -
	 * ys[i]) * zs[i]; return xs; }
	 * 
	 * public static double[] vaddsmul(double[] x, double[] ys, double z){ final
	 * double[] xs = x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = (xs[i] +
	 * ys[i]) * z; return xs; }
	 * 
	 * public static double[] vsubsmul(double[] x, double[] ys, double z){ final
	 * double[] xs = x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = (xs[i] -
	 * ys[i]) * z; return xs; }
	 * 
	 * public static double[] vmulsadd(double[] x, double[] ys, double z){ final
	 * double[] xs = x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = (xs[i] *
	 * ys[i]) + z; return xs; }
	 * 
	 * public static double[] vdiv(double[] x, double[] ys){ final double[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] /= ys[i]; return xs; }
	 * 
	 * public static double[] vmul(double[] x, double[] ys){ final double[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] *= ys[i]; return xs; }
	 * 
	 * public static double[] vmuladd(double[] x, double[] ys, double[] zs){ final
	 * double[] xs = x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = (xs[i] *
	 * ys[i]) + zs[i]; return xs; }
	 * 
	 * public static double[] vmulsub(double[] x, double[] ys, double[] zs){ final
	 * double[] xs = x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = (xs[i] *
	 * ys[i]) - zs[i]; return xs; }
	 * 
	 * public static double[] vmax(double[] x, double[] ys){ final double[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = Math.max(xs[i], ys[i]);
	 * return xs; }
	 * 
	 * public static double[] vmin(double[] x, double[] ys){ final double[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = Math.min(xs[i], ys[i]);
	 * return xs; }
	 * 
	 * public static double[] vmap(IFn fn, double[] x) { double[] xs = x.clone();
	 * for(int i = 0; i < xs.length; i++) xs[i] = ((Number)
	 * fn.invoke(xs[i])).doubleValue(); return xs; }
	 * 
	 * public static double[] vmap(IFn fn, double[] x, double[] ys) { double[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = ((Number)
	 * fn.invoke(xs[i], ys[i])).doubleValue(); return xs; } }
	 * 
	 * public static class I{ public static int add(int x, int y){ return x + y; }
	 * 
	 * public static int subtract(int x, int y){ return x - y; }
	 * 
	 * public static int negate(int x){ return -x; }
	 * 
	 * public static int inc(int x){ return x + 1; }
	 * 
	 * public static int dec(int x){ return x - 1; }
	 * 
	 * public static int multiply(int x, int y){ return x * y; }
	 * 
	 * public static int divide(int x, int y){ return x / y; }
	 * 
	 * public static boolean eq(int x, int y){ return x == y; }
	 * 
	 * public static boolean lt(int x, int y){ return x < y; }
	 * 
	 * public static boolean lte(int x, int y){ return x <= y; }
	 * 
	 * public static boolean gt(int x, int y){ return x > y; }
	 * 
	 * public static boolean gte(int x, int y){ return x >= y; }
	 * 
	 * public static boolean pos(int x){ return x > 0; }
	 * 
	 * public static boolean neg(int x){ return x < 0; }
	 * 
	 * public static boolean zero(int x){ return x == 0; }
	 * 
	 * public static int aget(int[] xs, int i){ return xs[i]; }
	 * 
	 * public static int aset(int[] xs, int i, int v){ xs[i] = v; return v; }
	 * 
	 * public static int alength(int[] xs){ return xs.length; }
	 * 
	 * public static int[] aclone(int[] xs){ return xs.clone(); }
	 * 
	 * public static int[] vec(int size, Object init){ int[] ret = new int[size];
	 * if(init instanceof Number) { int f = ((Number) init).intValue(); for(int i =
	 * 0; i < ret.length; i++) ret[i] = f; } else { A.Seq<?> s = Coll.toSeq(init);
	 * for(int i = 0; i < size && s != null; i++, s = s.rest()) ret[i] = ((Number)
	 * s.first()).intValue(); } return ret; }
	 * 
	 * public static int[] vec(Object sizeOrSeq){ if(sizeOrSeq instanceof Number)
	 * return new int[((Number) sizeOrSeq).intValue()]; else { A.Seq<?> s =
	 * Coll.toSeq(sizeOrSeq); int size = s.count(); int[] ret = new int[size]; for(int i
	 * = 0; i < size && s != null; i++, s = s.rest()) ret[i] = ((Number)
	 * s.first()).intValue(); return ret; } }
	 * 
	 * public static int[] vsadd(int[] x, int y){ final int[] xs = x.clone();
	 * for(int i = 0; i < xs.length; i++) xs[i] += y; return xs; }
	 * 
	 * public static int[] vssub(int[] x, int y){ final int[] xs = x.clone();
	 * for(int i = 0; i < xs.length; i++) xs[i] -= y; return xs; }
	 * 
	 * public static int[] vsdiv(int[] x, int y){ final int[] xs = x.clone();
	 * for(int i = 0; i < xs.length; i++) xs[i] /= y; return xs; }
	 * 
	 * public static int[] vsmul(int[] x, int y){ final int[] xs = x.clone();
	 * for(int i = 0; i < xs.length; i++) xs[i] *= y; return xs; }
	 * 
	 * public static int[] svdiv(int y, int[] x){ final int[] xs = x.clone();
	 * for(int i = 0; i < xs.length; i++) xs[i] = y / xs[i]; return xs; }
	 * 
	 * public static int[] vsmuladd(int[] x, int y, int[] zs){ final int[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = xs[i] * y + zs[i];
	 * return xs; }
	 * 
	 * public static int[] vsmulsub(int[] x, int y, int[] zs){ final int[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = xs[i] * y - zs[i];
	 * return xs; }
	 * 
	 * public static int[] vsmulsadd(int[] x, int y, int z){ final int[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = xs[i] * y + z; return
	 * xs; }
	 * 
	 * public static int[] vsmulssub(int[] x, int y, int z){ final int[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = xs[i] * y - z; return
	 * xs; }
	 * 
	 * public static int[] vabs(int[] x){ final int[] xs = x.clone(); for(int i = 0;
	 * i < xs.length; i++) xs[i] = Math.abs(xs[i]); return xs; }
	 * 
	 * public static int[] vnegabs(int[] x){ final int[] xs = x.clone(); for(int i =
	 * 0; i < xs.length; i++) xs[i] = -Math.abs(xs[i]); return xs; }
	 * 
	 * public static int[] vneg(int[] x){ final int[] xs = x.clone(); for(int i = 0;
	 * i < xs.length; i++) xs[i] = -xs[i]; return xs; }
	 * 
	 * public static int[] vsqr(int[] x){ final int[] xs = x.clone(); for(int i = 0;
	 * i < xs.length; i++) xs[i] *= xs[i]; return xs; }
	 * 
	 * public static int[] vsignedsqr(int[] x){ final int[] xs = x.clone(); for(int
	 * i = 0; i < xs.length; i++) xs[i] *= Math.abs(xs[i]); return xs; }
	 * 
	 * public static int[] vclip(int[] x, int low, int high){ final int[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) { if(xs[i] < low) xs[i] = low;
	 * else if(xs[i] > high) xs[i] = high; } return xs; }
	 * 
	 * public static IPersistentVector vclipcounts(int[] x, int low, int high){
	 * final int[] xs = x.clone(); int lowc = 0; int highc = 0;
	 * 
	 * for(int i = 0; i < xs.length; i++) { if(xs[i] < low) { ++lowc; xs[i] = low; }
	 * else if(xs[i] > high) { ++highc; xs[i] = high; } } return Ut.vector(xs, lowc,
	 * highc); }
	 * 
	 * public static int[] vthresh(int[] x, int thresh, int otherwise){ final int[]
	 * xs = x.clone(); for(int i = 0; i < xs.length; i++) { if(xs[i] < thresh) xs[i]
	 * = otherwise; } return xs; }
	 * 
	 * public static int[] vreverse(int[] x){ final int[] xs = x.clone(); for(int i
	 * = 0; i < xs.length; i++) xs[i] = xs[xs.length - i - 1]; return xs; }
	 * 
	 * public static int[] vrunningsum(int[] x){ final int[] xs = x.clone(); for(int
	 * i = 1; i < xs.length; i++) xs[i] = xs[i - 1] + xs[i]; return xs; }
	 * 
	 * public static int[] vsort(int[] x){ final int[] xs = x.clone();
	 * Arrays.sort(xs); return xs; }
	 * 
	 * public static int vdot(int[] xs, int[] ys){ int ret = 0; for(int i = 0; i <
	 * xs.length; i++) ret += xs[i] * ys[i]; return ret; }
	 * 
	 * public static int vmax(int[] xs){ if(xs.length == 0) return 0; int ret =
	 * xs[0]; for(int i = 0; i < xs.length; i++) ret = Math.max(ret, xs[i]); return
	 * ret; }
	 * 
	 * public static int vmin(int[] xs){ if(xs.length == 0) return 0; int ret =
	 * xs[0]; for(int i = 0; i < xs.length; i++) ret = Math.min(ret, xs[i]); return
	 * ret; }
	 * 
	 * public static double vmean(int[] xs){ if(xs.length == 0) return 0; return
	 * vsum(xs) / (double) xs.length; }
	 * 
	 * public static double vrms(int[] xs){ if(xs.length == 0) return 0; int ret =
	 * 0; for(int i = 0; i < xs.length; i++) ret += xs[i] * xs[i]; return
	 * Math.sqrt(ret / (double) xs.length); }
	 * 
	 * public static int vsum(int[] xs){ int ret = 0; for(int i = 0; i < xs.length;
	 * i++) ret += xs[i]; return ret; }
	 * 
	 * public static boolean veq(int[] xs, int[] ys){ return Arrays.equals(xs, ys);
	 * }
	 * 
	 * public static int[] vadd(int[] x, int[] ys){ final int[] xs = x.clone();
	 * for(int i = 0; i < xs.length; i++) xs[i] += ys[i]; return xs; }
	 * 
	 * public static int[] vsub(int[] x, int[] ys){ final int[] xs = x.clone();
	 * for(int i = 0; i < xs.length; i++) xs[i] -= ys[i]; return xs; }
	 * 
	 * public static int[] vaddmul(int[] x, int[] ys, int[] zs){ final int[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = (xs[i] + ys[i]) *
	 * zs[i]; return xs; }
	 * 
	 * public static int[] vsubmul(int[] x, int[] ys, int[] zs){ final int[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = (xs[i] - ys[i]) *
	 * zs[i]; return xs; }
	 * 
	 * public static int[] vaddsmul(int[] x, int[] ys, int z){ final int[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = (xs[i] + ys[i]) * z;
	 * return xs; }
	 * 
	 * public static int[] vsubsmul(int[] x, int[] ys, int z){ final int[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = (xs[i] - ys[i]) * z;
	 * return xs; }
	 * 
	 * public static int[] vmulsadd(int[] x, int[] ys, int z){ final int[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = (xs[i] * ys[i]) + z;
	 * return xs; }
	 * 
	 * public static int[] vdiv(int[] x, int[] ys){ final int[] xs = x.clone();
	 * for(int i = 0; i < xs.length; i++) xs[i] /= ys[i]; return xs; }
	 * 
	 * public static int[] vmul(int[] x, int[] ys){ final int[] xs = x.clone();
	 * for(int i = 0; i < xs.length; i++) xs[i] *= ys[i]; return xs; }
	 * 
	 * public static int[] vmuladd(int[] x, int[] ys, int[] zs){ final int[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = (xs[i] * ys[i]) +
	 * zs[i]; return xs; }
	 * 
	 * public static int[] vmulsub(int[] x, int[] ys, int[] zs){ final int[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = (xs[i] * ys[i]) -
	 * zs[i]; return xs; }
	 * 
	 * public static int[] vmax(int[] x, int[] ys){ final int[] xs = x.clone();
	 * for(int i = 0; i < xs.length; i++) xs[i] = Math.max(xs[i], ys[i]); return xs;
	 * }
	 * 
	 * public static int[] vmin(int[] x, int[] ys){ final int[] xs = x.clone();
	 * for(int i = 0; i < xs.length; i++) xs[i] = Math.min(xs[i], ys[i]); return xs;
	 * }
	 * 
	 * public static int[] vmap(IFn fn, int[] x) { int[] xs = x.clone(); for(int i =
	 * 0; i < xs.length; i++) xs[i] = ((Number) fn.invoke(xs[i])).intValue(); return
	 * xs; }
	 * 
	 * public static int[] vmap(IFn fn, int[] x, int[] ys) { int[] xs = x.clone();
	 * for(int i = 0; i < xs.length; i++) xs[i] = ((Number) fn.invoke(xs[i],
	 * ys[i])).intValue(); return xs; }
	 * 
	 * }
	 * 
	 * public static class L{ public static long add(long x, long y){ return x + y;
	 * }
	 * 
	 * public static long subtract(long x, long y){ return x - y; }
	 * 
	 * public static long negate(long x){ return -x; }
	 * 
	 * public static long inc(long x){ return x + 1; }
	 * 
	 * public static long dec(long x){ return x - 1; }
	 * 
	 * public static long multiply(long x, long y){ return x * y; }
	 * 
	 * public static long divide(long x, long y){ return x / y; }
	 * 
	 * public static boolean eq(long x, long y){ return x == y; }
	 * 
	 * public static boolean lt(long x, long y){ return x < y; }
	 * 
	 * public static boolean lte(long x, long y){ return x <= y; }
	 * 
	 * public static boolean gt(long x, long y){ return x > y; }
	 * 
	 * public static boolean gte(long x, long y){ return x >= y; }
	 * 
	 * public static boolean pos(long x){ return x > 0; }
	 * 
	 * public static boolean neg(long x){ return x < 0; }
	 * 
	 * public static boolean zero(long x){ return x == 0; }
	 * 
	 * public static long aget(long[] xs, int i){ return xs[i]; }
	 * 
	 * public static long aset(long[] xs, int i, long v){ xs[i] = v; return v; }
	 * 
	 * public static int alength(long[] xs){ return xs.length; }
	 * 
	 * public static long[] aclone(long[] xs){ return xs.clone(); }
	 * 
	 * public static long[] vec(int size, Object init){ long[] ret = new long[size];
	 * if(init instanceof Number) { long f = ((Number) init).longValue(); for(int i
	 * = 0; i < ret.length; i++) ret[i] = f; } else { A.Seq<?> s = Coll.toSeq(init);
	 * for(int i = 0; i < size && s != null; i++, s = s.rest()) ret[i] = ((Number)
	 * s.first()).longValue(); } return ret; }
	 * 
	 * public static long[] vec(Object sizeOrSeq){ if(sizeOrSeq instanceof Number)
	 * return new long[((Number) sizeOrSeq).intValue()]; else { A.Seq<?> s =
	 * Coll.toSeq(sizeOrSeq); int size = s.count(); long[] ret = new long[size]; for(int
	 * i = 0; i < size && s != null; i++, s = s.rest()) ret[i] = ((Number)
	 * s.first()).intValue(); return ret; } }
	 * 
	 * 
	 * public static long[] vsadd(long[] x, long y){ final long[] xs = x.clone();
	 * for(int i = 0; i < xs.length; i++) xs[i] += y; return xs; }
	 * 
	 * public static long[] vssub(long[] x, long y){ final long[] xs = x.clone();
	 * for(int i = 0; i < xs.length; i++) xs[i] -= y; return xs; }
	 * 
	 * public static long[] vsdiv(long[] x, long y){ final long[] xs = x.clone();
	 * for(int i = 0; i < xs.length; i++) xs[i] /= y; return xs; }
	 * 
	 * public static long[] vsmul(long[] x, long y){ final long[] xs = x.clone();
	 * for(int i = 0; i < xs.length; i++) xs[i] *= y; return xs; }
	 * 
	 * public static long[] svdiv(long y, long[] x){ final long[] xs = x.clone();
	 * for(int i = 0; i < xs.length; i++) xs[i] = y / xs[i]; return xs; }
	 * 
	 * public static long[] vsmuladd(long[] x, long y, long[] zs){ final long[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = xs[i] * y + zs[i];
	 * return xs; }
	 * 
	 * public static long[] vsmulsub(long[] x, long y, long[] zs){ final long[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = xs[i] * y - zs[i];
	 * return xs; }
	 * 
	 * public static long[] vsmulsadd(long[] x, long y, long z){ final long[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = xs[i] * y + z; return
	 * xs; }
	 * 
	 * public static long[] vsmulssub(long[] x, long y, long z){ final long[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = xs[i] * y - z; return
	 * xs; }
	 * 
	 * public static long[] vabs(long[] x){ final long[] xs = x.clone(); for(int i =
	 * 0; i < xs.length; i++) xs[i] = Math.abs(xs[i]); return xs; }
	 * 
	 * public static long[] vnegabs(long[] x){ final long[] xs = x.clone(); for(int
	 * i = 0; i < xs.length; i++) xs[i] = -Math.abs(xs[i]); return xs; }
	 * 
	 * public static long[] vneg(long[] x){ final long[] xs = x.clone(); for(int i =
	 * 0; i < xs.length; i++) xs[i] = -xs[i]; return xs; }
	 * 
	 * public static long[] vsqr(long[] x){ final long[] xs = x.clone(); for(int i =
	 * 0; i < xs.length; i++) xs[i] *= xs[i]; return xs; }
	 * 
	 * public static long[] vsignedsqr(long[] x){ final long[] xs = x.clone();
	 * for(int i = 0; i < xs.length; i++) xs[i] *= Math.abs(xs[i]); return xs; }
	 * 
	 * public static long[] vclip(long[] x, long low, long high){ final long[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) { if(xs[i] < low) xs[i] = low;
	 * else if(xs[i] > high) xs[i] = high; } return xs; }
	 * 
	 * public static IPersistentVector vclipcounts(long[] x, long low, long high){
	 * final long[] xs = x.clone(); int lowc = 0; int highc = 0;
	 * 
	 * for(int i = 0; i < xs.length; i++) { if(xs[i] < low) { ++lowc; xs[i] = low; }
	 * else if(xs[i] > high) { ++highc; xs[i] = high; } } return Ut.vector(xs, lowc,
	 * highc); }
	 * 
	 * public static long[] vthresh(long[] x, long thresh, long otherwise){ final
	 * long[] xs = x.clone(); for(int i = 0; i < xs.length; i++) { if(xs[i] <
	 * thresh) xs[i] = otherwise; } return xs; }
	 * 
	 * public static long[] vreverse(long[] x){ final long[] xs = x.clone(); for(int
	 * i = 0; i < xs.length; i++) xs[i] = xs[xs.length - i - 1]; return xs; }
	 * 
	 * public static long[] vrunningsum(long[] x){ final long[] xs = x.clone();
	 * for(int i = 1; i < xs.length; i++) xs[i] = xs[i - 1] + xs[i]; return xs; }
	 * 
	 * public static long[] vsort(long[] x){ final long[] xs = x.clone();
	 * Arrays.sort(xs); return xs; }
	 * 
	 * public static long vdot(long[] xs, long[] ys){ long ret = 0; for(int i = 0; i
	 * < xs.length; i++) ret += xs[i] * ys[i]; return ret; }
	 * 
	 * public static long vmax(long[] xs){ if(xs.length == 0) return 0; long ret =
	 * xs[0]; for(int i = 0; i < xs.length; i++) ret = Math.max(ret, xs[i]); return
	 * ret; }
	 * 
	 * public static long vmin(long[] xs){ if(xs.length == 0) return 0; long ret =
	 * xs[0]; for(int i = 0; i < xs.length; i++) ret = Math.min(ret, xs[i]); return
	 * ret; }
	 * 
	 * public static double vmean(long[] xs){ if(xs.length == 0) return 0; return
	 * vsum(xs) / (double) xs.length; }
	 * 
	 * public static double vrms(long[] xs){ if(xs.length == 0) return 0; long ret =
	 * 0; for(int i = 0; i < xs.length; i++) ret += xs[i] * xs[i]; return
	 * Math.sqrt(ret / (double) xs.length); }
	 * 
	 * public static long vsum(long[] xs){ long ret = 0; for(int i = 0; i <
	 * xs.length; i++) ret += xs[i]; return ret; }
	 * 
	 * public static boolean veq(long[] xs, long[] ys){ return Arrays.equals(xs,
	 * ys); }
	 * 
	 * public static long[] vadd(long[] x, long[] ys){ final long[] xs = x.clone();
	 * for(int i = 0; i < xs.length; i++) xs[i] += ys[i]; return xs; }
	 * 
	 * public static long[] vsub(long[] x, long[] ys){ final long[] xs = x.clone();
	 * for(int i = 0; i < xs.length; i++) xs[i] -= ys[i]; return xs; }
	 * 
	 * public static long[] vaddmul(long[] x, long[] ys, long[] zs){ final long[] xs
	 * = x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = (xs[i] + ys[i]) *
	 * zs[i]; return xs; }
	 * 
	 * public static long[] vsubmul(long[] x, long[] ys, long[] zs){ final long[] xs
	 * = x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = (xs[i] - ys[i]) *
	 * zs[i]; return xs; }
	 * 
	 * public static long[] vaddsmul(long[] x, long[] ys, long z){ final long[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = (xs[i] + ys[i]) * z;
	 * return xs; }
	 * 
	 * public static long[] vsubsmul(long[] x, long[] ys, long z){ final long[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = (xs[i] - ys[i]) * z;
	 * return xs; }
	 * 
	 * public static long[] vmulsadd(long[] x, long[] ys, long z){ final long[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = (xs[i] * ys[i]) + z;
	 * return xs; }
	 * 
	 * public static long[] vdiv(long[] x, long[] ys){ final long[] xs = x.clone();
	 * for(int i = 0; i < xs.length; i++) xs[i] /= ys[i]; return xs; }
	 * 
	 * public static long[] vmul(long[] x, long[] ys){ final long[] xs = x.clone();
	 * for(int i = 0; i < xs.length; i++) xs[i] *= ys[i]; return xs; }
	 * 
	 * public static long[] vmuladd(long[] x, long[] ys, long[] zs){ final long[] xs
	 * = x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = (xs[i] * ys[i]) +
	 * zs[i]; return xs; }
	 * 
	 * public static long[] vmulsub(long[] x, long[] ys, long[] zs){ final long[] xs
	 * = x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = (xs[i] * ys[i]) -
	 * zs[i]; return xs; }
	 * 
	 * public static long[] vmax(long[] x, long[] ys){ final long[] xs = x.clone();
	 * for(int i = 0; i < xs.length; i++) xs[i] = Math.max(xs[i], ys[i]); return xs;
	 * }
	 * 
	 * public static long[] vmin(long[] x, long[] ys){ final long[] xs = x.clone();
	 * for(int i = 0; i < xs.length; i++) xs[i] = Math.min(xs[i], ys[i]); return xs;
	 * }
	 * 
	 * public static long[] vmap(IFn fn, long[] x) { long[] xs = x.clone(); for(int
	 * i = 0; i < xs.length; i++) xs[i] = ((Number) fn.invoke(xs[i])).longValue();
	 * return xs; }
	 * 
	 * public static long[] vmap(IFn fn, long[] x, long[] ys) { long[] xs =
	 * x.clone(); for(int i = 0; i < xs.length; i++) xs[i] = ((Number)
	 * fn.invoke(xs[i], ys[i])).longValue(); return xs; }
	 * 
	 * }
	 */

//overload resolution
//*

	public static long setBit(long x, long n) {
		return x | (1L << n);
	}

	public static long setBit(long x, Object y) {
		return setBit(x, bitOpsCast(y));
	}

	public static long setBit(Object x, long y) {
		return setBit(bitOpsCast(x), y);
	}

	public static long setBit(Object x, Object y) {
		return setBit(bitOpsCast(x), bitOpsCast(y));
	}

	public static long shiftLeft(long x, long n) {
		return x << n;
	}

	public static long shiftLeft(long x, Object y) {
		return shiftLeft(x, bitOpsCast(y));
	}

	public static long shiftLeft(Object x, long y) {
		return shiftLeft(bitOpsCast(x), y);
	}

	public static long shiftLeft(Object x, Object y) {
		return shiftLeft(bitOpsCast(x), bitOpsCast(y));
	}

	public static int shiftLeftInt(int x, int n) {
		return x << n;
	}

	public static long shiftRight(long x, long n) {
		return x >> n;
	}

	public static long shiftRight(long x, Object y) {
		return shiftRight(x, bitOpsCast(y));
	}

	public static long shiftRight(Object x, long y) {
		return shiftRight(bitOpsCast(x), y);
	}

	public static long shiftRight(Object x, Object y) {
		return shiftRight(bitOpsCast(x), bitOpsCast(y));
	}

	public static int shiftRightInt(int x, int n) {
		return x >> n;
	}

	// @WarnBoxedMath(false)
	public static short[] shorts(Object array) {
		return (short[]) array;
	}

	public static boolean testBit(long x, long n) {
		return (x & (1L << n)) != 0;
	}

	public static boolean testBit(long x, Object y) {
		return testBit(x, bitOpsCast(y));
	}

	public static boolean testBit(Object x, long y) {
		return testBit(bitOpsCast(x), y);
	}

	public static boolean testBit(Object x, Object y) {
		return testBit(bitOpsCast(x), bitOpsCast(y));
	}

	static int throwIntOverflow() {
		throw new ArithmeticException("integer overflow");
	}

	// @WarnBoxedMath(false)
	static BigDecimal toBigDecimal(Object x) {
		if (x instanceof BigDecimal)
			return (BigDecimal) x;
		else if (x instanceof BigInteger)
			return new BigDecimal((BigInteger) x);
		else if (x instanceof Double)
			return new BigDecimal(((Number) x).doubleValue());
		else if (x instanceof Float)
			return new BigDecimal(((Number) x).doubleValue());
		else if (x instanceof Ratio) {
			Ratio r = (Ratio) x;
			return (BigDecimal) divide(new BigDecimal(r.numerator), r.denominator);
		} else
			return BigDecimal.valueOf(((Number) x).longValue());
	}

	// @WarnBoxedMath(false)
	static BigInteger toBigInteger(Object x) {
		if (x instanceof BigInteger)
			return (BigInteger) x;
		else
			return BigInteger.valueOf(((Number) x).longValue());
	}

	// @WarnBoxedMath(false)
	public static Ratio toRatio(Object x) {
		if (x instanceof Ratio)
			return (Ratio) x;
		else if (x instanceof BigDecimal) {
			BigDecimal bx = (BigDecimal) x;
			BigInteger bv = bx.unscaledValue();
			int scale = bx.scale();
			if (scale < 0)
				return new Ratio(bv.multiply(BigInteger.TEN.pow(-scale)), BigInteger.ONE);
			else
				return new Ratio(bv, BigInteger.TEN.pow(scale));
		}
		return new Ratio(toBigInteger(x), BigInteger.ONE);
	}

	public static double unchecked_add(double x, double y) {
		return add(x, y);
	}

	public static double unchecked_add(double x, long y) {
		return add(x, y);
	}

	public static double unchecked_add(double x, Object y) {
		return add(x, y);
	}

	public static double unchecked_add(long x, double y) {
		return add(x, y);
	}

	public static long unchecked_add(long x, long y) {
		return x + y;
	}

	public static Number unchecked_add(long x, Object y) {
		return unchecked_add((Object) x, y);
	}

	public static double unchecked_add(Object x, double y) {
		return add(x, y);
	}

	public static Number unchecked_add(Object x, long y) {
		return unchecked_add(x, (Object) y);
	}

	public static Number unchecked_add(Object x, Object y) {
		return ops(x).combine(ops(y)).unchecked_add((Number) x, (Number) y);
	}

	public static double unchecked_dec(double x) {
		return dec(x);
	}

	public static long unchecked_dec(long x) {
		return x - 1;
	}

	public static Number unchecked_dec(Object x) {
		return ops(x).unchecked_dec((Number) x);
	}

	public static double unchecked_inc(double x) {
		return inc(x);
	}

	public static long unchecked_inc(long x) {
		return x + 1;
	}

	public static Number unchecked_inc(Object x) {
		return ops(x).unchecked_inc((Number) x);
	}

	public static int unchecked_int_add(int x, int y) {
		return x + y;
	}

	public static int unchecked_int_dec(int x) {
		return x - 1;
	}

	public static int unchecked_int_divide(int x, int y) {
		return x / y;
	}

	public static int unchecked_int_inc(int x) {
		return x + 1;
	}

	public static int unchecked_int_multiply(int x, int y) {
		return x * y;
	}

	public static int unchecked_int_negate(int x) {
		return -x;
	}

	public static int unchecked_int_remainder(int x, int y) {
		return x % y;
	}

	public static int unchecked_int_subtract(int x, int y) {
		return x - y;
	}

	public static double unchecked_minus(double x) {
		return minus(x);
	}

	public static double unchecked_minus(double x, double y) {
		return minus(x, y);
	}

	public static double unchecked_minus(double x, long y) {
		return minus(x, y);
	}

	public static double unchecked_minus(double x, Object y) {
		return minus(x, y);
	}

	public static long unchecked_minus(long x) {
		return -x;
	}

	public static double unchecked_minus(long x, double y) {
		return minus(x, y);
	}

	public static long unchecked_minus(long x, long y) {
		return x - y;
	}

	public static Number unchecked_minus(long x, Object y) {
		return unchecked_minus((Object) x, y);
	}

	public static Number unchecked_minus(Object x) {
		return ops(x).unchecked_negate((Number) x);
	}

	public static double unchecked_minus(Object x, double y) {
		return minus(x, y);
	}

	public static Number unchecked_minus(Object x, long y) {
		return unchecked_minus(x, (Object) y);
	}

	public static Number unchecked_minus(Object x, Object y) {
		Ops yops = ops(y);
		return ops(x).combine(yops).unchecked_add((Number) x, yops.unchecked_negate((Number) y));
	}

	public static double unchecked_multiply(double x, double y) {
		return multiply(x, y);
	}

	public static double unchecked_multiply(double x, long y) {
		return multiply(x, y);
	}

	public static double unchecked_multiply(double x, Object y) {
		return multiply(x, y);
	}

	public static double unchecked_multiply(long x, double y) {
		return multiply(x, y);
	}

	public static long unchecked_multiply(long x, long y) {
		return x * y;
	}

	public static Number unchecked_multiply(long x, Object y) {
		return unchecked_multiply((Object) x, y);
	}

	public static double unchecked_multiply(Object x, double y) {
		return multiply(x, y);
	}

	public static Number unchecked_multiply(Object x, long y) {
		return unchecked_multiply(x, (Object) y);
	}

	public static Number unchecked_multiply(Object x, Object y) {
		return ops(x).combine(ops(y)).unchecked_multiply((Number) x, (Number) y);
	}

	public static long unsignedShiftRight(long x, long n) {
		return x >>> n;
	}

	public static long unsignedShiftRight(long x, Object y) {
		return unsignedShiftRight(x, bitOpsCast(y));
	}

	public static long unsignedShiftRight(Object x, long y) {
		return unsignedShiftRight(bitOpsCast(x), y);
	}

	public static long unsignedShiftRight(Object x, Object y) {
		return unsignedShiftRight(bitOpsCast(x), bitOpsCast(y));
	}

	public static int unsignedShiftRightInt(int x, int n) {
		return x >>> n;
	}

	public static long xor(long x, long y) {
		return x ^ y;
	}

	public static long xor(long x, Object y) {
		return xor(x, bitOpsCast(y));
	}
	
	public static long xor(Object x, long y) {
		return xor(bitOpsCast(x), y);
	}


	public static long xor(Object x, Object y) {
		return xor(bitOpsCast(x), bitOpsCast(y));
	}

}
