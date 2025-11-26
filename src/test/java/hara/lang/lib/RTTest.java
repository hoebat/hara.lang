package hara.lang.lib;

import junit.framework.TestCase;

import hara.lang.base.I;
import hara.lang.data.Symbol;

public class RTTest extends TestCase {

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

    public void testInstance() {
        RT.Instance<Object> rt = new RT.Instance<>(null, "test");
        assertNotNull(rt);
    }

    public void testEval() {
        RT.Instance<Object> rt = new RT.Instance<>(null, "test");
        Object result = rt.eval(rt.readString("(+ 1 2)"));
        assertEquals(3L, result);
    }

    public void testSetAndGetObj() {
        RT.Instance<Object> rt = new RT.Instance<>(null, "test");
        Symbol sym = Symbol.create("a");
        hara.lang.lib.Var var = new hara.lang.lib.Var(sym.getName(), 10);
        rt.setObj(sym, var);
        assertEquals(var, rt.getObj(sym));
    }
}
