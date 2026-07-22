package hara.truffle;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import hara.pod.v1.Handle;
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
}
