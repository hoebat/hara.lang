package hara.lang.base.primitive;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

public class Ratio extends Number implements Comparable<Object> {

  private static final long serialVersionUID = 1L;
  public final BigInteger numerator;
  public final BigInteger denominator;

  public Ratio(BigInteger numerator, BigInteger denominator) {
    this.numerator = numerator;
    this.denominator = denominator;
  }

  @Override
  public boolean equals(Object arg0) {
    return arg0 != null
        && arg0 instanceof Ratio
        && ((Ratio) arg0).numerator.equals(numerator)
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
