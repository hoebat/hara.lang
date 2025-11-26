package hara.lang.compiler;

import java.util.function.Function;
import junit.framework.TestCase;
import hara.lang.kernel.Foundation;

public class CompilerTest extends TestCase {

    @SuppressWarnings("unchecked")
    public void testCompile() {
        Foundation f = new Foundation();
        try {
            Object result = f.call("COMPILE", "(fn [x] (+ x 1))");
            assertNotNull(result);
            assertTrue(result instanceof Function);
            Function<Long, Long> fn = (Function<Long, Long>) result;
            assertEquals(Long.valueOf(2L), fn.apply(1L));
        } catch (Exception e) {
            fail("Should not have thrown an exception: " + e.getMessage());
        }
    }
}
