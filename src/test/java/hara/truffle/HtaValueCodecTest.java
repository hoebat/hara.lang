package hara.truffle;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import hara.lang.data.Keyword;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class HtaValueCodecTest {
  @Test
  public void encodesTheHta1GoldenVector() {
    byte[] encoded = HtaValueCodec.encode(List.of("x", 42L, true));
    assertArrayEquals(
        new byte[] {
          'H', 'T', 'A', '1', 9, 0, 0, 0, 3, 4, 0, 0, 0, 1, 'x', 3, 0, 0, 0, 0, 0, 0, 0, 42, 2
        },
        encoded);
    assertEquals(List.of("x", 42L, true), HtaValueCodec.decode(encoded));
  }

  @Test
  public void mapEncodingIsCanonical() {
    Map<Object, Object> left = new LinkedHashMap<>();
    left.put(Keyword.create("b"), 2L);
    left.put(Keyword.create("a"), 1L);
    Map<Object, Object> right = new LinkedHashMap<>();
    right.put(Keyword.create("a"), 1L);
    right.put(Keyword.create("b"), 2L);
    assertArrayEquals(HtaValueCodec.encode(left), HtaValueCodec.encode(right));
  }

  @Test
  public void rejectsTrailingAndTruncatedFrames() {
    byte[] valid = HtaValueCodec.encode("ok");
    assertThrows(
        HaraException.class, () -> HtaValueCodec.decode(Arrays.copyOf(valid, valid.length - 1)));
    assertThrows(
        HaraException.class, () -> HtaValueCodec.decode(Arrays.copyOf(valid, valid.length + 1)));
  }
}
