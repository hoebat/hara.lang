package hara.lang.base;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.function.Function;
import hara.lang.data.Vector;
import hara.lang.base.Eq;

public class RequestTest {

    static class MockFn implements I.Client {
        @Override
        public Function<Object, Object> getArgN() {
            return (args) -> {
                if (args instanceof Object[]) {
                    return Vector.Standard.from(null, (Object[]) args);
                }
                return args;
            };
        }

        @Override
        public Function<Object, Object> getArg1() {
            return (arg) -> arg;
        }
    }

    private void assertVectorEquals(Vector<Object> expected, Object actual) {
        assertTrue(actual instanceof Vector);
        @SuppressWarnings("unchecked")
        Vector<Object> actualVector = (Vector<Object>) actual;
        assertEquals(expected.count(), actualVector.count());
        for (int i = 0; i < expected.count(); i++) {
            assertTrue("Elements at index " + i + " are not equal.",
                       Eq.eq(expected.nth(i), actualVector.nth(i)));
        }
    }

    @Test
    public void testRequest() {
        Request request = new Request();
        I.Client mockClient = new MockFn();

        // Test requestSingle
        assertEquals("test", request.requestSingle(mockClient, "test", null));
        assertVectorEquals(Vector.Standard.from(null, "a", "b"), request.requestSingle(mockClient, new Object[]{"a", "b"}, null));

        // Test processSingle
        assertEquals("output", request.processSingle(mockClient, "output", null));

        // Test requestBulk
        @SuppressWarnings("unchecked")
        Vector<Object> commands = Vector.Standard.from(null, "cmd1", new Object[]{"cmd2", "arg"});
        @SuppressWarnings("unchecked")
        Vector<Object> expectedBulkResult = Vector.Standard.from(null, "cmd1", Vector.Standard.from(null, "cmd2", "arg"));
        assertVectorEquals(expectedBulkResult, request.requestBulk(mockClient, commands, null));

        // Test processBulk
        Vector<Object> outputs = Vector.Standard.from(null, "out1", "out2");
        assertEquals(outputs, request.processBulk(mockClient, null, outputs, null));

        // Test transact methods (still returning null)
        assertNull(request.transactStart(mockClient));
        assertNull(request.transactEnd(mockClient));
        assertNull(request.transactCombine(mockClient, null));
    }
}
