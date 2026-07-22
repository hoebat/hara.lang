package hara.truffle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import hara.lang.data.List;
import hara.lang.data.Map;
import hara.lang.data.OrderedMap;
import hara.lang.data.OrderedSet;
import hara.lang.data.Queue;
import hara.lang.data.Set;
import hara.lang.data.SortedMap;
import hara.lang.data.SortedSet;
import hara.lang.data.Tuple;
import hara.lang.data.Vector;
import java.util.Iterator;
import java.util.Map.Entry;
import org.junit.Test;

/** Protocol-level collection checks shared by Hara-native and Java-backed values. */
public class CollectionProtocolConformanceTest {
  @Test
  public void lookupPreservesMissingAndPresentNullValues() {
    HaraProtocol lookup = new HaraProtocol("ILookup", java.util.Map.of("lookup", -1));
    HaraJavaAdapters.installLookup(lookup);
    Map.Standard<String, String> map = Map.Standard.from(null, "present", null);

    assertEquals(null, lookup.invoke("lookup", map, new Object[] {"present"}));
    assertEquals("missing", lookup.invoke("lookup", map, new Object[] {"missing", "missing"}));
  }

  @Test
  public void countAndIterationCoverPersistentMutableAndOrderedShapes() {
    HaraProtocol count = new HaraProtocol("ICount", java.util.Map.of("count", 1));
    HaraJavaAdapters.installCount(count);
    HaraProtocol collection =
        new HaraProtocol(
            "IColl",
            java.util.Map.of("start-string", 1, "end-string", 1, "sep-string", 1, "iterator", 1));
    HaraJavaAdapters.installCollection(collection);

    Object[][] values = {
      {Map.Standard.from(null, "a", 1, "b", 2), 2L},
      {Map.Mutable.from(null, "a", 1, "b", 2), 2L},
      {OrderedMap.Standard.from(null, "a", 1, "b", 2), 2L},
      {SortedMap.Standard.from(null, "b", 2, "a", 1), 2L},
      {Set.Standard.from(null, "a", "b"), 2L},
      {OrderedSet.Standard.from(null, "a", "b"), 2L},
      {SortedSet.Standard.from(null, "b", "a"), 2L},
      {Vector.Standard.from(null, "a", "b"), 2L},
      {List.Standard.from(null, "a", "b"), 2L},
      {Queue.Standard.from(null, "a", "b"), 2L},
      {new Tuple.Tup2.L<>(null, "a", "b"), 2L}
    };

    for (Object[] value : values) {
      Object receiver = value[0];
      assertEquals(value[1], count.invoke("count", receiver, new Object[0]));
      Iterator<?> iterator = (Iterator<?>) collection.invoke("iterator", receiver, new Object[0]);
      assertTrue(iterator.hasNext());
      iterator.next();
    }
  }

  @Test
  public void mapInvocationAndSetInvocationUseNotFoundArguments() {
    HaraProtocol ifn = new HaraProtocol("IFn", java.util.Map.of("invoke", -1));
    HaraJavaAdapters.installIFn(ifn);

    Map.Standard<String, String> map = Map.Standard.from(null, "key", "value");
    assertEquals("value", ifn.invoke("invoke", map, new Object[] {"key"}));
    assertEquals("missing", ifn.invoke("invoke", map, new Object[] {"missing", "missing"}));

    Set.Standard<String> set = Set.Standard.from(null, "present");
    assertEquals("present", ifn.invoke("invoke", set, new Object[] {"present"}));
    assertEquals("missing", ifn.invoke("invoke", set, new Object[] {"missing", "missing"}));
  }

  @Test
  public void persistentUpdatesDoNotMutateTheOriginalValue() {
    HaraProtocol assoc = new HaraProtocol("IAssoc", java.util.Map.of("assoc", 3));
    HaraJavaAdapters.installAssoc(assoc);
    HaraProtocol lookup = new HaraProtocol("ILookup", java.util.Map.of("lookup", -1));
    HaraJavaAdapters.installLookup(lookup);

    Vector.Standard<String> original = Vector.Standard.from(null, "old");
    Object updated = assoc.invoke("assoc", original, new Object[] {0, "new"});
    assertEquals("old", lookup.invoke("lookup", original, new Object[] {0L}));
    assertEquals("new", lookup.invoke("lookup", updated, new Object[] {0L}));
  }

  @Test
  public void emptyAndConsRemainProtocolOperations() {
    HaraProtocol empty = new HaraProtocol("IEmpty", java.util.Map.of("empty", 1));
    HaraJavaAdapters.installEmpty(empty);
    HaraProtocol cons = new HaraProtocol("ICons", java.util.Map.of("cons", 2));
    HaraJavaAdapters.installCons(cons);
    HaraProtocol count = new HaraProtocol("ICount", java.util.Map.of("count", 1));
    HaraJavaAdapters.installCount(count);

    List.Standard<String> list = List.Standard.from(null, "tail");
    Object emptyList = empty.invoke("empty", list, new Object[0]);
    assertEquals(0L, count.invoke("count", emptyList, new Object[0]));
    Object prepended = cons.invoke("cons", list, new Object[] {"head"});
    assertEquals(2L, count.invoke("count", prepended, new Object[0]));
    assertFalse(count.invoke("count", list, new Object[0]).equals(0L));
  }
}
