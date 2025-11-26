package hara.lang.lib;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import hara.lang.data.Symbol;
import hara.lang.data.List;
import hara.lang.data.Vector;

public class MacroTest {

    private RT.Instance<Object> rt;

    public static class TestClass {
        public String testField = "hello";
    }

    @Before
    public void setUp() {
        rt = new RT.Instance<>(null, "test");
        rt.setObj(Symbol.create("test-instance"), new Var("test-instance", new TestClass()));
    }

    @Test
    public void testIfMacro() {
        List ifExpr = List.Standard.from(null, Symbol.create("if"), true, 1, 2);
        assertEquals(1, rt.eval(ifExpr));

        List elseExpr = List.Standard.from(null, Symbol.create("if"), false, 1, 2);
        assertEquals(2, rt.eval(elseExpr));
    }

    @Test
    public void testDoMacro() {
        List doExpr = List.Standard.from(null, Symbol.create("do"),
            List.Standard.from(null, Symbol.create("def"), Symbol.create("a"), 10),
            Symbol.create("a"));
        assertEquals(10, rt.eval(doExpr));
    }

    @Test
    public void testDotMacro() {
        List dotExpr = List.Standard.from(null, Symbol.create("."), Symbol.create("test-instance"), Symbol.create("testField"));
        assertEquals("hello", rt.eval(dotExpr));
    }
}
