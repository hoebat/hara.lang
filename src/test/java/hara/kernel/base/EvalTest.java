package hara.kernel.base;

import hara.kernel.protocol.IEnv;
import hara.kernel.protocol.IRuntime;
import hara.lang.data.Symbol;
import hara.lang.protocol.ILookup;
import org.junit.Test;

import java.util.Iterator;
import java.util.Map;

import static org.junit.Assert.*;

public class EvalTest {

  @Test
  public void testLocalEnv() {
    // Mock parent env
    IEnv parent =
        new IEnv() {
          @Override
          public Map.Entry find(Object key) {
            return null;
          }

          @Override
          public IEnv getParent() {
            return null;
          }

          @Override
          public ILookup getMap() {
            return null;
          }

          @Override
          public IRuntime getRuntime() {
            return null;
          }

          @Override
          public Iterator keys() {
            return java.util.Collections.emptyIterator();
          }

          @Override
          public Iterator vals() {
            return java.util.Collections.emptyIterator();
          }
        };

    Eval.LocalEnv env = new Eval.LocalEnv(parent);
    Symbol symA = Symbol.create("a");
    Symbol symB = Symbol.create("b");

    // Test addBinding and find
    env.addBinding(symA, 1L);
    assertNotNull(env.find(symA));
    assertEquals(1L, env.find(symA).getValue());
    assertNull(env.find(symB));

    // Test getMap
    ILookup map = env.getMap();
    assertNotNull(map.find(symA));
    assertEquals(1L, ((Map.Entry) map.find(symA)).getValue());
    assertNull(map.find(symB));

    // Test keys and vals
    Iterator keys = env.keys();
    assertTrue(keys.hasNext());
    assertEquals(symA, keys.next());

    Iterator vals = env.vals();
    assertTrue(vals.hasNext());
    assertEquals(1L, vals.next());
  }
}
