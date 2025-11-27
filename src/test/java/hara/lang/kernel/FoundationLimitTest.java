package hara.lang.kernel;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import hara.lang.base.Ex;
import hara.lang.base.I;

public class FoundationLimitTest {

    @Test
    public void testSessionWithLimit() {
        Foundation f = new Foundation();

        long limit = 500 * 1024; // 500KB

        // Create session with 500KB limit
        List<String> createArgs = new ArrayList<>(Arrays.asList("SESSION", "NEW", "testLimit", String.valueOf(limit)));
        Foundation.runCommand(f, createArgs);

        // Check info
        List<String> infoArgs = new ArrayList<>(Arrays.asList("SESSION", "INFO", "testLimit"));
        Object infoObj = Foundation.runCommand(f, infoArgs);

        assertTrue(infoObj instanceof I.Lookup);
        I.Lookup info = (I.Lookup) infoObj;

        Object limitObj = info.lookup(hara.lang.data.Keyword.create("limit"));
        assertNotNull(limitObj);
        assertEquals(limit, ((Number)limitObj).longValue());

        // Exceed limit
        // We define a large list to fill up memory.
        // We use a loop to build a list because we are not sure if 'vec' or 'range' behave as expected (lazy vs eager).
        // (def x (loop [i 0 l []] (if (< i 20000) (recur (+ i 1) (conj l i)) l)))
        // 20000 integers + list nodes should exceed 500KB (20k * 20 bytes ~ 400KB + overhead)

        String bigList = "(def x (loop [i 0 l []] (if (< i 20000) (recur (+ i 1) (conj l i)) l)))";
        List<String> evalArgs1 = new ArrayList<>(Arrays.asList("EVAL", "testLimit", bigList));

        try {
             Foundation.runCommand(f, evalArgs1);
        } catch (Throwable t) {
            // It might fail here if the loop execution checks limit? No, only entry.
            // Or if it runs out of Java Heap? Unlikely.
        }

        // Now check limit with a simple command
        List<String> evalArgs2 = new ArrayList<>(Arrays.asList("EVAL", "testLimit", "1"));

        try {
            Foundation.runCommand(f, evalArgs2);
            fail("Should have thrown Memory Limit Exceeded on second call");
        } catch (Ex.Runtime e) {
            assertTrue("Message was: " + e.getMessage(), e.getMessage().contains("Memory Limit Exceeded"));
        } catch (RuntimeException e) {
             if (e.getCause() instanceof Ex.Runtime && e.getCause().getMessage().contains("Memory Limit Exceeded")) {
                 // OK
             } else {
                 throw e;
             }
        }
    }
}
