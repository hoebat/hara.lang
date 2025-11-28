package hara.kernel.base;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import hara.lang.data.Symbol;
import hara.lang.data.List;
import java.math.BigInteger;

public class ControlTest {

    private RT.Instance<Object> rt;

    @Before
    public void setUp() {
        rt = new RT.Instance<>(null, "test");
    }

    @Test
    public void testTryCatch() {
        String code = "(try (throw (new java.lang.RuntimeException \"error\")) (catch java.lang.RuntimeException e \"caught\"))";
        Object ast = rt.readString(code);
        Object result = rt.eval(ast);
        assertEquals("caught", result);
    }

    @Test
    public void testTryFinally() {
         String code = "(let [a (atom \"init\")] (try \"ok\" (finally (reset! a \"done\"))) @a)";
         Object ast = rt.readString(code);
         Object result = rt.eval(ast);
         assertEquals("done", result);
    }

    @Test
    public void testLoopRecur() {
        // (loop [i 0 acc 0] (if (< i 5) (recur (inc i) (+ acc i)) acc))
        // Sum of 0, 1, 2, 3, 4 = 10
        String code = "(loop [i 0 acc 0] (if (< i 5) (recur (inc i) (+ acc i)) acc))";
        Object ast = rt.readString(code);
        Object result = rt.eval(ast);
        assertEquals(BigInteger.valueOf(10L), result);
    }

    @Test
    public void testLet() {
        String code = "(let [a 1 b 2] (+ a b))";
        assertEquals(BigInteger.valueOf(3L), rt.eval(rt.readString(code)));
    }

    @Test
    public void testIf() {
        assertEquals(1L, rt.eval(rt.readString("(if true 1 2)")));
        assertEquals(2L, rt.eval(rt.readString("(if false 1 2)")));
        assertEquals(1L, rt.eval(rt.readString("(if true 1)")));
        assertNull(rt.eval(rt.readString("(if false 1)")));
    }
}
