package hara.lang.lib;

import junit.framework.TestCase;
import hara.lang.data.Symbol;
import hara.lang.data.List;
import hara.lang.data.Vector;

public class MacroTest extends TestCase {

    private RT.Instance<Object> rt;

    @Override
    protected void setUp() {
        rt = new RT.Instance<>(null, "test");
    }

    public void testIfMacro() {
        List ifExpr = List.Standard.from(null, Symbol.create("if"), true, 1, 2);
        assertEquals(1, rt.eval(ifExpr));

        List elseExpr = List.Standard.from(null, Symbol.create("if"), false, 1, 2);
        assertEquals(2, rt.eval(elseExpr));
    }

    public void testDoMacro() {
        List doExpr = List.Standard.from(null, Symbol.create("do"),
            List.Standard.from(null, Symbol.create("def"), Symbol.create("a"), 10),
            Symbol.create("a"));
        assertEquals(10, rt.eval(doExpr));
    }

    public void testDotMacro() {
        rt.setObj(Symbol.create("s"), new Var("s", "hello"));
        List dotExpr = List.Standard.from(null, Symbol.create("."), Symbol.create("s"), Symbol.create("length"));
        assertEquals(5, rt.eval(dotExpr));
    }
}
