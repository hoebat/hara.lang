package hara.truffle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.Test;

public class HaraNumericInteropTest {
  private static final BigInteger LARGE_INTEGER = new BigInteger("123456789012345678901234567890");

  @Test
  public void exportsBigIntegersAsExactPolyglotNumbers() throws Exception {
    InteropLibrary interop = InteropLibrary.getUncached();
    Object exported = HaraBox.export(LARGE_INTEGER);

    assertTrue(interop.isNumber(exported));
    assertTrue(interop.fitsInBigInteger(exported));
    assertFalse(interop.fitsInLong(exported));
    assertFalse(interop.fitsInDouble(exported));
    assertEquals(LARGE_INTEGER, interop.asBigInteger(exported));
    assertThrows(UnsupportedMessageException.class, () -> interop.asLong(exported));
  }

  @Test
  public void exportsDecimalsAsCanonicalExactValueObjects() throws Exception {
    InteropLibrary interop = InteropLibrary.getUncached();
    Object exported = HaraBox.export(new BigDecimal("1.2300"));

    assertFalse(interop.isNumber(exported));
    assertTrue(interop.hasMembers(exported));
    assertEquals("1.23", interop.readMember(exported, "value"));
    assertEquals(2, interop.readMember(exported, "scale"));
    assertEquals(3, interop.readMember(exported, "precision"));
    Object unscaled = interop.readMember(exported, "unscaled");
    assertEquals(BigInteger.valueOf(123), interop.asBigInteger(unscaled));
    assertEquals("1.23M", interop.toDisplayString(exported, false));
  }

  @Test
  public void preservesExactNumbersAcrossThePolyglotBoundary() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      Value integer = context.eval(HaraLanguage.ID, LARGE_INTEGER.toString());
      assertTrue(integer.isNumber());
      assertEquals(LARGE_INTEGER, integer.as(BigInteger.class));

      Value decimal = context.eval(HaraLanguage.ID, "1.2300M");
      assertFalse(decimal.isNumber());
      assertTrue(decimal.hasMembers());
      assertEquals("1.23", decimal.getMember("value").asString());
      assertEquals(2, decimal.getMember("scale").asInt());
    }
  }
}
