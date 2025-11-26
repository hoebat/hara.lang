package hara.lang.lib;

import junit.framework.TestCase;
import hara.lang.data.Symbol;
import hara.lang.data.Map;

public class EnvTest extends TestCase {

    public void testLoadStatic() {
        Map<Symbol, Var> staticVars = Env.loadStatic();
        assertNotNull(staticVars);
        assertTrue(staticVars.count() > 0);

        // Check for a few important symbols
        assertNotNull(staticVars.find(Symbol.create("+")));
        assertNotNull(staticVars.find(Symbol.create("map")));
        assertNotNull(staticVars.find(Symbol.create("reduce")));
    }
}
