package hara.truffle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import hara.lang.data.Atom;
import hara.lang.data.Keyword;
import hara.lang.data.Map;
import hara.lang.data.Vector;
import hara.lang.protocol.Constant;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.Test;

/** Acceptance matrix for one language-agnostic registry over guest and Java-backed values. */
public class LanguageAgnosticProtocolAcceptanceTest {
  @Test
  public void oneProtocolRegistryHandlesHaraStructAndJavaCollection() {
    try (Context context = Context.newBuilder(HaraLanguage.ID).build()) {
      Value measure =
          context.eval(
              HaraLanguage.ID,
              "(do "
                  + "  (defstruct Box [size]) "
                  + "  (extend-type Box ICount (count [self] (field self :size))) "
                  + "  (fn [value] (protocol-call ICount count value)))");

      assertEquals(3L, measure.execute(Vector.Standard.from(null, "a", "b", "c")).asLong());
      assertEquals(2L, measure.execute(Map.Standard.from(null, "a", 1, "b", 2)).asLong());
      assertEquals(
          4L, context.eval(HaraLanguage.ID, "(protocol-call ICount count (Box 4))").asLong());
    }
  }

  @Test
  public void adaptsFunctionStateAndMetadataProtocolsThroughTheSameBoundary() {
    HaraProtocol ifn = new HaraProtocol("IFn", java.util.Map.of("invoke", -1));
    HaraJavaAdapters.installIFn(ifn);
    Map.Standard<String, String> map = Map.Standard.from(null, "key", "value");
    assertEquals("value", ifn.invoke("invoke", map, new Object[] {"key"}));

    HaraProtocol deref = new HaraProtocol("IDeref", java.util.Map.of("deref", 1));
    HaraJavaAdapters.installDeref(deref);
    Atom.Basic<Long> atom = new Atom.Basic<>(41L);
    assertEquals(41L, deref.invoke("deref", atom, new Object[0]));

    HaraProtocol reset = new HaraProtocol("IReset", java.util.Map.of("reset", 2));
    HaraJavaAdapters.installReset(reset);
    assertEquals(42L, reset.invoke("reset", atom, new Object[] {42L}));
    assertEquals(42L, deref.invoke("deref", atom, new Object[0]));

    HaraProtocol metadata =
        new HaraProtocol("IObjType", java.util.Map.of("meta", 1, "with-meta", 2));
    HaraJavaAdapters.installMetadata(metadata);
    Keyword keyword = Keyword.create("core/value");
    assertNull(metadata.invoke("meta", keyword, new Object[0]));

    HaraProtocol metadataValue = new HaraProtocol("IMetadata", java.util.Map.of("metatype", 1));
    HaraJavaAdapters.installMetadataValue(metadataValue);
    assertEquals(
        Constant.MetaType.STRING, metadataValue.invoke("metatype", keyword, new Object[0]));
  }

  @Test
  public void haraStructsCanBeExtendedForFunctionAndMetadataProtocols() {
    HaraType type = new HaraType("CallableBox", new String[] {"value"});
    HaraStruct value = new HaraStruct(type, new Object[] {41L});

    HaraProtocol ifn = new HaraProtocol("IFn", java.util.Map.of("invoke", -1));
    HaraJavaAdapters.installIFn(ifn);
    ifn.extend(type, "invoke", (receiver, arguments) -> 41L + ((Number) arguments[0]).longValue());
    assertEquals(43L, ifn.invoke("invoke", value, new Object[] {2L}));

    HaraProtocol metadata = new HaraProtocol("IMetadata", java.util.Map.of("metatype", 1));
    HaraJavaAdapters.installMetadataValue(metadata);
    metadata.extend(type, "metatype", (receiver, arguments) -> Constant.MetaType.OBJECT);
    assertEquals(Constant.MetaType.OBJECT, metadata.invoke("metatype", value, new Object[0]));
    assertTrue(value.toString().contains("CallableBox"));
  }
}
