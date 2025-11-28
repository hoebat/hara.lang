package hara.kernel.base;

import hara.lang.data.Symbol;
import org.junit.Test;

import java.math.BigInteger;

import static org.junit.Assert.*;

public class RTTest {

  @Test
  public void testLoader() {
    RT.Loader loader = new RT.Loader();
    try {
      Class<?> stringClass = loader.loadClass("java.lang.String", false);
      assertNotNull("Should be able to load java.lang.String", stringClass);
      assertEquals("java.lang.String", stringClass.getName());
    } catch (ClassNotFoundException e) {
      fail("ClassNotFoundException for java.lang.String");
    }
  }

  @Test
  public void testInstance() {
    RT.Instance<Object> rt = new RT.Instance<>(null, "test");
    assertNotNull(rt);
  }

  @Test
  public void testEval() {
    RT.Instance<Object> rt = new RT.Instance<>(null, "test");
    Object result = rt.eval(rt.readString("(+ 1 2)"));
    assertEquals(BigInteger.valueOf(3L), result);
  }

  @Test
  public void testSetAndGetObj() {
    RT.Instance<Object> rt = new RT.Instance<>(null, "test");
    Symbol sym = Symbol.create("a");
    hara.kernel.base.Var var = new hara.kernel.base.Var(sym.getName(), 10);
    rt.setObj(sym, var);
    assertEquals(var, rt.getObj(sym));
  }
}
