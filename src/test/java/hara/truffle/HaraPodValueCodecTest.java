package hara.truffle;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.google.protobuf.ByteString;
import hara.pod.v1.BigIntegerValue;
import hara.pod.v1.Handle;
import hara.pod.v1.Value;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class HaraPodValueCodecTest {
  @Test
  public void roundTripsNestedPortableValuesAndHandles() {
    HaraPodHandle handle =
        new HaraPodHandle(Handle.newBuilder().setId(9).setType("tensor").setOwner("pod").build());
    Object decoded =
        HaraPodValueCodec.decode(
            HaraPodValueCodec.encode(
                Map.of("name", "Ada", "values", List.of(1L, true), "handle", handle)));

    Map<?, ?> map = (Map<?, ?>) decoded;
    assertEquals("Ada", map.get("name"));
    assertEquals(List.of(1L, true), map.get("values"));
    assertEquals(handle.handle(), ((HaraPodHandle) map.get("handle")).handle());
  }

  @Test
  public void preservesBytesAndNull() {
    assertArrayEquals(
        new byte[] {1, 2, 3},
        (byte[]) HaraPodValueCodec.decode(HaraPodValueCodec.encode(new byte[] {1, 2, 3})));
    assertEquals(HaraNull.SINGLETON, HaraPodValueCodec.decode(HaraPodValueCodec.encode(null)));
  }

  @Test
  public void roundTripsExactIntegersAndCanonicalDecimals() {
    for (BigInteger value :
        List.of(
            BigInteger.ZERO,
            BigInteger.valueOf(-129),
            new BigInteger("123456789012345678901234567890"))) {
      assertEquals(value, HaraPodValueCodec.decode(HaraPodValueCodec.encode(value)));
    }

    assertEquals(
        new BigDecimal("1.23"),
        HaraPodValueCodec.decode(HaraPodValueCodec.encode(new BigDecimal("1.2300"))));
    assertEquals(
        new BigDecimal("1E+3"),
        HaraPodValueCodec.decode(HaraPodValueCodec.encode(new BigDecimal("1000"))));
    assertEquals(
        new BigInteger("123456789012345678901234567890"),
        HaraPodValueCodec.decode(
            HaraPodValueCodec.encode(
                HaraBox.export(new BigInteger("123456789012345678901234567890")))));
    assertEquals(
        new BigDecimal("1.23"),
        HaraPodValueCodec.decode(
            HaraPodValueCodec.encode(HaraBox.export(new BigDecimal("1.2300")))));
  }

  @Test
  public void rejectsNonCanonicalBigIntegerBytes() {
    Value empty =
        Value.newBuilder()
            .setBigInteger(BigIntegerValue.newBuilder().setTwosComplement(ByteString.EMPTY))
            .build();
    Value signExtended =
        Value.newBuilder()
            .setBigInteger(
                BigIntegerValue.newBuilder()
                    .setTwosComplement(ByteString.copyFrom(new byte[] {0, 1})))
            .build();

    assertThrows(IllegalArgumentException.class, () -> HaraPodValueCodec.decode(empty));
    assertThrows(IllegalArgumentException.class, () -> HaraPodValueCodec.decode(signExtended));
  }
}
