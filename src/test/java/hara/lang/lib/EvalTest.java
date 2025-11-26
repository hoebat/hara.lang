package hara.lang.lib;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import hara.lang.base.I;
import hara.lang.data.Symbol;
import hara.lang.data.List;
import hara.lang.data.Map;
import hara.lang.data.Keyword;

public class EvalTest {

    private RT.Instance<Object> rt;

    @Before
    public void setUp() {
        rt = new RT.Instance<>(null, "test");
    }

    @Test
    public void testEvalSymbol() {
        Symbol sym = Symbol.create("a");
        rt.setObj(sym, new Var(sym.getName(), 10));
        assertEquals(10, Eval.eval(sym, rt.getEnv()));
    }

    @Test
    public void testEvalList() {
        List list = List.Standard.from(null, Symbol.create("+"), 1, 2);
        assertEquals(3L, Eval.eval(list, rt.getEnv()));
    }

    @Test
    public void testEvalMap() {
        Map map = Map.Standard.from(null, Keyword.create("a"), 1, Keyword.create("b"), 2);
        Map resultMap = (Map) Eval.eval(map, rt.getEnv());
        assertEquals(1, resultMap.lookup(Keyword.create("a")));
        assertEquals(2, resultMap.lookup(Keyword.create("b")));
    }
}
