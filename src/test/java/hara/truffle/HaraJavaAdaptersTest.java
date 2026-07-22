package hara.truffle;

import static org.junit.Assert.assertEquals;

import hara.lang.data.Atom;
import hara.lang.data.List;
import hara.lang.data.Keyword;
import hara.lang.data.SortedMap;
import hara.lang.data.Vector;
import hara.lang.base.primitive.Counter;
import hara.lang.base.primitive.Delay;
import java.util.Iterator;
import java.util.Map;
import org.junit.Test;

public class HaraJavaAdaptersTest {
  @Test
  public void adaptsPersistentCollectionsWithoutChangingTheirInterfaces() {
    HaraProtocol lookup = new HaraProtocol("ILookup", Map.of("lookup", -1));
    HaraJavaAdapters.installLookup(lookup);
    Vector.Standard<String> vector = Vector.Standard.from(null, "zero", "one");

    assertEquals("one", lookup.invoke("lookup", vector, new Object[] {1L}));

    HaraProtocol assoc = new HaraProtocol("IAssoc", Map.of("assoc", 3));
    HaraJavaAdapters.installAssoc(assoc);
    assertEquals(
        "value",
        lookup.invoke(
            "lookup", assoc.invoke("assoc", vector, new Object[] {0, "value"}), new Object[] {0L}));
  }

  @Test
  public void adaptsMapsFunctionsAndStatefulValues() {
    HaraProtocol lookup = new HaraProtocol("ILookup", Map.of("lookup", -1));
    HaraJavaAdapters.installLookup(lookup);
    hara.lang.data.Map.Standard<String, String> map =
        hara.lang.data.Map.Standard.from(null, "key", "value");
    assertEquals("value", lookup.invoke("lookup", map, new Object[] {"key"}));

    HaraProtocol ifn = new HaraProtocol("IFn", Map.of("invoke", -1));
    HaraJavaAdapters.installIFn(ifn);
    assertEquals("value", ifn.invoke("invoke", map, new Object[] {"key"}));

    HaraProtocol deref = new HaraProtocol("IDeref", Map.of("deref", 1));
    HaraJavaAdapters.installDeref(deref);
    Atom.Basic<Long> atom = new Atom.Basic<>(41L);
    assertEquals(41L, deref.invoke("deref", atom, new Object[0]));
  }

  @Test
  public void adaptsCollectionNavigationAndConversion() {
    List.Standard<String> list = List.Standard.from(null, "one", "two");
    HaraProtocol navigation =
        new HaraProtocol(
            "INavigation",
            Map.of(
                "peek-first",
                1,
                "peek-last",
                1,
                "pop-first",
                1,
                "pop-last",
                1,
                "push-first",
                2,
                "push-last",
                2));
    HaraJavaAdapters.installNavigation(navigation);
    assertEquals("one", navigation.invoke("peek-first", list, new Object[0]));
    assertEquals("two", navigation.invoke("peek-last", list, new Object[0]));
    assertEquals(
        "zero",
        navigation.invoke(
            "peek-first",
            navigation.invoke("push-first", list, new Object[] {"zero"}),
            new Object[0]));

    HaraProtocol cons = new HaraProtocol("ICons", Map.of("cons", 2));
    HaraJavaAdapters.installCons(cons);
    assertEquals(
        "zero",
        navigation.invoke(
            "peek-first", cons.invoke("cons", list, new Object[] {"zero"}), new Object[0]));

    HaraProtocol toMutable = new HaraProtocol("IToMutable", Map.of("to-mutable", 1));
    HaraJavaAdapters.installConversion(
        toMutable, new HaraProtocol("IToPersistent", Map.of("to-persistent", 1)));
    assertEquals(
        "hara.lang.data.List$Mutable",
        toMutable.invoke("to-mutable", list, new Object[0]).getClass().getName());
  }

  @Test
  public void adaptsStateNamespaceAndIndexedProtocols() {
    Counter counter = new Counter(1);
    HaraProtocol reset = new HaraProtocol("IReset", Map.of("reset", 2));
    HaraJavaAdapters.installReset(reset);
    assertEquals(42, reset.invoke("reset", counter, new Object[] {42}));

    Delay<String> delay = new Delay<>(() -> "ready");
    HaraProtocol realize = new HaraProtocol("IRealize", Map.of("realized?", 1, "realize", 1));
    HaraJavaAdapters.installRealize(realize);
    assertEquals(false, realize.invoke("realized?", delay, new Object[0]));
    assertEquals("ready", realize.invoke("realize", delay, new Object[0]));
    assertEquals(true, realize.invoke("realized?", delay, new Object[0]));

    HaraProtocol namespaced = new HaraProtocol("INamespaced", Map.of("name", 1, "namespace", 1));
    HaraJavaAdapters.installNamespaced(namespaced);
    Keyword keyword = Keyword.create("core/value");
    assertEquals("value", namespaced.invoke("name", keyword, new Object[0]));
    assertEquals("core", namespaced.invoke("namespace", keyword, new Object[0]));

    HaraProtocol indexed =
        new HaraProtocol("IIndexedKV", Map.of("index-of-key", 2, "index-of-val", 2));
    HaraJavaAdapters.installIndexedKV(indexed);
    SortedMap.Standard<Integer, String> sorted = SortedMap.Standard.from(null, 1, "one", 2, "two");
    assertEquals(1L, indexed.invoke("index-of-key", sorted, new Object[] {2}));
  }

  @Test
  public void exposesIteratorTraversalThroughTheCollectionProtocol() {
    HaraProtocol collection =
        new HaraProtocol(
            "IColl",
            Map.of("start-string", 1, "end-string", 1, "sep-string", 1, "iterator", 1));
    HaraJavaAdapters.installCollection(collection);
    Vector.Standard<String> vector = Vector.Standard.from(null, "first", "second");

    Iterator<?> iterator = (Iterator<?>) collection.invoke("iterator", vector, new Object[0]);
    assertEquals("first", iterator.next());
    assertEquals("second", iterator.next());
  }
}
