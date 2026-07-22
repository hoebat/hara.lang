package hara.truffle;

import static org.junit.Assert.assertEquals;

import hara.lang.data.List;
import hara.lang.data.Map;
import hara.lang.data.Queue;
import hara.lang.data.Set;
import hara.lang.data.SortedMap;
import hara.lang.data.Tuple;
import hara.lang.data.Vector;
import hara.lang.protocol.IColl;
import hara.lang.protocol.ICount;
import hara.lang.protocol.ILookup;
import hara.lang.protocol.IPair;
import java.util.ArrayList;
import java.util.Iterator;
import org.junit.Test;

/** Differential checks between protocol calls and the existing Java collection behavior. */
public class CollectionProtocolDifferentialTest {
  @Test
  public void protocolCountAndIterationMatchJavaValues() {
    HaraProtocol count = new HaraProtocol("ICount", java.util.Map.of("count", 1));
    HaraJavaAdapters.installCount(count);
    HaraProtocol collection =
        new HaraProtocol(
            "IColl",
            java.util.Map.of("start-string", 1, "end-string", 1, "sep-string", 1, "iterator", 1));
    HaraJavaAdapters.installCollection(collection);

    Object[] values = {
      Map.Standard.from(null, "a", 1, "b", 2),
      Vector.Standard.from(null, "a", "b"),
      List.Standard.from(null, "a", "b"),
      Set.Standard.from(null, "a", "b"),
      Queue.Standard.from(null, "a", "b"),
      new Tuple.Tup2.L<>(null, "a", "b"),
      SortedMap.Standard.from(null, "b", 2, "a", 1)
    };

    for (Object value : values) {
      ICount javaValue = (ICount) value;
      assertEquals(javaValue.count(), count.invoke("count", value, new Object[0]));

      ArrayList<Object> expected = new ArrayList<>();
      for (Object element : (Iterable<?>) value) {
        expected.add(comparable(element));
      }
      Iterator<?> protocolIterator =
          (Iterator<?>) collection.invoke("iterator", value, new Object[0]);
      ArrayList<Object> actual = new ArrayList<>();
      protocolIterator.forEachRemaining(element -> actual.add(comparable(element)));
      assertEquals(expected, actual);
    }
  }

  private static Object comparable(Object value) {
    if (value instanceof IPair) {
      IPair<?, ?> pair = (IPair<?, ?>) value;
      return java.util.Arrays.asList(pair.getKey(), pair.getValue());
    }
    return value;
  }

  @Test
  public void lookupAndInvocationPreserveNullAndNotFoundSemantics() {
    HaraProtocol lookup = new HaraProtocol("ILookup", java.util.Map.of("lookup", -1));
    HaraJavaAdapters.installLookup(lookup);
    HaraProtocol ifn = new HaraProtocol("IFn", java.util.Map.of("invoke", -1));
    HaraJavaAdapters.installIFn(ifn);

    Map.Standard<String, String> map = Map.Standard.from(null, "present", null);
    ILookup<String, String> javaMap = map;
    assertEquals(javaMap.lookup("present"), lookup.invoke("lookup", map, new Object[] {"present"}));
    assertEquals("missing", lookup.invoke("lookup", map, new Object[] {"missing", "missing"}));
    assertEquals("missing", ifn.invoke("invoke", map, new Object[] {"missing", "missing"}));
  }

  @Test
  public void indexedAndSetInvocationMatchDirectValues() {
    HaraProtocol ifn = new HaraProtocol("IFn", java.util.Map.of("invoke", -1));
    HaraJavaAdapters.installIFn(ifn);

    Vector.Standard<String> vector = Vector.Standard.from(null, "zero", "one");
    assertEquals(vector.nth(1L), ifn.invoke("invoke", vector, new Object[] {1L}));

    Set.Standard<String> set = Set.Standard.from(null, "present");
    assertEquals("present", ifn.invoke("invoke", set, new Object[] {"present"}));
    assertEquals("missing", ifn.invoke("invoke", set, new Object[] {"missing", "missing"}));
    assertEquals("present", set.find("present"));
  }
}
