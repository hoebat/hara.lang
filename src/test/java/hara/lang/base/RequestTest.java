package hara.lang.base;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.function.Function;
import java.util.Arrays;
import java.util.List;

public class RequestTest {

    static class MockFn implements I.OFn {
        @Override
        public Function<Object, Object> getArgN() {
            return (args) -> {
                if (args instanceof Object[]) {
                    return Arrays.asList((Object[]) args);
                }
                return args;
            };
        }

        @Override
        public Function<Object, Object> getArg1() {
            return (arg) -> arg;
        }
    }

    @Test
    public void testRequest() {
        Request request = new Request();
        I.OFn mockClient = new MockFn();

        // Test requestSingle
        assertEquals("test", request.requestSingle(mockClient, "test", null));
        assertEquals(Arrays.asList("a", "b"), request.requestSingle(mockClient, new Object[]{"a", "b"}, null));

        // Test processSingle
        assertEquals("output", request.processSingle(mockClient, "output", null));

        // Test requestBulk
        List<Object> commands = Arrays.asList("cmd1", new Object[]{"cmd2", "arg"});
        List<Object> expectedBulkResult = Arrays.asList("cmd1", Arrays.asList("cmd2", "arg"));
        assertEquals(expectedBulkResult, request.requestBulk(mockClient, commands, null));

        // Test processBulk
        List<Object> outputs = Arrays.asList("out1", "out2");
        assertEquals(outputs, request.processBulk(mockClient, null, outputs, null));

        // Test transact methods (still returning null)
        assertNull(request.transactStart(mockClient));
        assertNull(request.transactEnd(mockClient));
        assertNull(request.transactCombine(mockClient, null));
    }
}
