package hara.truffle;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import hara.lang.base.primitive.Num;
import java.math.BigDecimal;
import java.math.BigInteger;

/** Explicit numeric conversions used at the Hara language boundary. */
final class HaraNumericConversions {
  private HaraNumericConversions() {}

  @TruffleBoundary
  static BigInteger toBigInteger(Object input) {
    Object value = unwrap(input);
    if (value instanceof BigInteger) return (BigInteger) value;
    if (value instanceof BigDecimal) return ((BigDecimal) value).toBigInteger();
    if (value instanceof Double || value instanceof Float) {
      double floating = ((Number) value).doubleValue();
      if (!Double.isFinite(floating)) {
        throw cannotConvert("bigint", input);
      }
      return BigDecimal.valueOf(floating).toBigInteger();
    }
    if (value instanceof Byte
        || value instanceof Short
        || value instanceof Integer
        || value instanceof Long) {
      return BigInteger.valueOf(((Number) value).longValue());
    }
    throw cannotConvert("bigint", input);
  }

  @TruffleBoundary
  static BigDecimal toBigDecimal(Object input) {
    Object value = unwrap(input);
    if (value instanceof BigDecimal) return Num.canonicalDecimal((BigDecimal) value);
    if (value instanceof BigInteger) {
      return Num.canonicalDecimal(new BigDecimal((BigInteger) value));
    }
    if (value instanceof Float) {
      float floating = (Float) value;
      if (!Float.isFinite(floating)) {
        throw cannotConvert("bigdec", input);
      }
      return Num.canonicalDecimal(new BigDecimal(Float.toString(floating)));
    }
    if (value instanceof Double) {
      double floating = (Double) value;
      if (!Double.isFinite(floating)) {
        throw cannotConvert("bigdec", input);
      }
      return Num.canonicalDecimal(BigDecimal.valueOf(floating));
    }
    if (value instanceof Byte
        || value instanceof Short
        || value instanceof Integer
        || value instanceof Long) {
      return Num.canonicalDecimal(BigDecimal.valueOf(((Number) value).longValue()));
    }
    throw cannotConvert("bigdec", input);
  }

  @TruffleBoundary
  static double toDouble(Object input) {
    Object value = unwrap(input);
    if (value instanceof Number) return ((Number) value).doubleValue();
    throw cannotConvert("double", input);
  }

  private static Object unwrap(Object value) {
    if (value instanceof HaraBigInteger) return ((HaraBigInteger) value).value();
    if (value instanceof HaraDecimal) return ((HaraDecimal) value).value();
    return value;
  }

  private static HaraException cannotConvert(String target, Object value) {
    return new HaraException(
        target + " expects a numeric value, got " + (value == null ? "nil" : value));
  }
}
