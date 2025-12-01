package hara.kernel.base;

import hara.kernel.protocol.IRuntime;
import hara.lang.data.Symbol;
import hara.lang.data.types.IMapType;
import org.junit.Test;
import static org.junit.Assert.*;

public class NamespaceTest {

  @Test
  public void testNsFind() {
    // This is tricky because we need an IRuntime instance.
    // RT.Instance requires IContext and String key.
    // Let's create a minimal RT.Instance if possible, or mock IRuntime.

    // Using a real RT.Instance might be complex due to dependencies.
    // Let's try to mock IRuntime for now, but Builtin.Namespace.nsFind casts to RT.Instance.
    // So we must use RT.Instance or a subclass.

    // RT.Instance constructor: public Instance(IContext root, String key)
    // IContext is an interface. We can implement a dummy one.

    hara.lang.protocol.IContext dummyContext =
        new hara.lang.protocol.IContext() {
          public Object call(Object... args) {
            return null;
          }
        };

    RT.Instance rt = new RT.Instance(dummyContext, "test");

    // Initially, there is a "user" namespace.
    Namespace userNs = Builtin.Namespace.nsFind(rt, Symbol.create("user"));
    assertNotNull(userNs);
    assertEquals("user", userNs.name.getName());

    // Create a new namespace
    Namespace newNs = Builtin.Namespace.nsCreate(rt, Symbol.create("new.ns"));
    assertNotNull(newNs);
    assertEquals("new.ns", newNs.name.getName());

    // Find it
    Namespace foundNs = Builtin.Namespace.nsFind(rt, Symbol.create("new.ns"));
    assertEquals(newNs, foundNs);

    // Check ns:map (mappings)
    IMapType mappings = Builtin.Namespace.nsMap(newNs);
    assertNotNull(mappings);

    // Check ns:list
    java.util.Iterator<Namespace> it = Builtin.Namespace.nsList(rt);
    int count = 0;
    while (it.hasNext()) {
      it.next();
      count++;
    }
    assertTrue(count >= 2); // user and new.ns
  }
}
