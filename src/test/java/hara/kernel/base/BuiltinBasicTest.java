package hara.kernel.base;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/** Simple tests for miscellaneous Builtin methods. */
public class BuiltinBasicTest {

  @Test
  public void testAddNumbers() {
    Number result = Builtin.Ops.add(10, 5);
    assertEquals(15L, result.longValue());
  }

  @Test
  public void testMultiplyNumbers() {
    Number result = Builtin.Ops.multiply(6, 7);
    assertEquals(42L, result.longValue());
  }

  @Test
  public void testHashMap() {
    Object result = Builtin.Struct.hashMap(java.util.Arrays.asList("key", "value"));
    assertNotNull(result);
  }

  @Test
  public void testVector() {
    Object result = Builtin.Struct.vector(java.util.Arrays.asList(1, 2, 3));
    assertNotNull(result);
  }

  @Test
  public void testJArr() {
    Object[] result = Builtin.Struct.jArr(java.util.Arrays.asList(1, 2, 3));
    assertNotNull(result);
    assertEquals(3, result.length);
  }

  @Test
  public void testIdentity() {
    String input = "test";
    Object result = Builtin.Lambda.identity(input);
    assertEquals(input, result);
  }
}
