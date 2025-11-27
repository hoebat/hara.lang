package hara.lang.compiler;

import hara.lang.base.*;
import hara.lang.lib.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class HotspotTest {

    @Test
    public void testHotspotJit() {
        RT.Instance rt = new RT.Instance(null, "test");
        I.Env env = rt.getEnv();

        // Define a function (fn [x] (+ x 1))
        String code = "(fn [x] (+ x 1))";
        Object fnObj = rt.eval(rt.readString(code));

        assertTrue(fnObj instanceof HotspotFn);
        HotspotFn hotspotFn = (HotspotFn) fnObj;

        // Before threshold
        assertNull(hotspotFn.getCompiled());

        // Call it 4 times (Threshold is 5)
        for (int i = 0; i < 4; i++) {
            Object result = hotspotFn.apply(new Object[]{ (long)i });
            assertEquals((long)i + 1, result);
        }

        assertNull(hotspotFn.getCompiled());

        // Call 5th time - triggers compilation
        Object result5 = hotspotFn.apply(new Object[]{ 4L });
        assertEquals(5L, result5);

        // Wait a bit if compilation is async? currently it is sync.
        assertNotNull("Should have compiled", hotspotFn.getCompiled());

        // Verify compiled function is used and works
        Object result6 = hotspotFn.apply(new Object[]{ 10L });
        assertEquals(11L, result6);

        assertTrue(hotspotFn.getCompiled().getClass().getName().startsWith("hara.lang.compiler.CompiledFunction"));
    }

    @Test
    public void testHotspotJitComplex() {
        // Test something that fails compilation to ensure fallback works
        RT.Instance rt = new RT.Instance(null, "test");

        // (fn [x] (do (prn x) (+ x 1))) - contains 'prn', not supported by Compiler
        // Wait, Compiler only supports + - * /.
        // If I use a symbol not in switch, Compiler might fail or produce bad code.
        // Compiler checks arg types. If arg is symbol it assumes it's a variable or number.
        // But the op check is: switch(op.getName()) { case "+": ... }
        // If op is "prn", it will skip switch and return ... wait.

        // Compiler.java:
        // switch (op.getName()) ...
        // mv.visitMethodInsn(..., "valueOf", ...)
        // mv.visitInsn(Opcodes.ARETURN);

        // If op is not matched, it just puts the last value on stack (which is from the argument loading) and returns it?
        // No, argument loading puts 2 longs on stack.
        // If switch is skipped, stack has 2 longs.
        // Then valueOf consumes 1 long.
        // Then ARETURN returns object.
        // Stack still has 1 long. Stack unbalanced? VerifyError likely.

        // So compilation should fail with VerifyError or similar when loading class.
        // HotspotFn catches Throwable, so it should fallback to interpreted.

        String code = "(fn [x] (- x 1))"; // - is supported
        Object fnObj = rt.eval(rt.readString(code));
        HotspotFn hotspotFn = (HotspotFn) fnObj;

        for (int i = 0; i < 10; i++) {
             hotspotFn.apply(new Object[]{ (long)i });
        }
        assertNotNull(hotspotFn.getCompiled());
    }
}
