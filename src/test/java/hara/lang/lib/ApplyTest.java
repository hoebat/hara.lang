package hara.lang.lib;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import hara.lang.base.I;
import hara.lang.base.I.Fn;

public class ApplyTest {

    private Fn createTestFn() {
        return new Fn<Object, Object, Object>() {
            @Override
            public Function<Object, Object> getArgN() {
                return args -> {
                    int sum = 0;
                    for (Object o : (Object[])args) {
                        sum += (int) o;
                    }
                    return sum;
                };
            }
        };
    }

    @Test
    public void testHostApplicativeSync() {
        HostApplicative app = new HostApplicative(createTestFn(), null, false, null);
        Object result = Apply.invokeAs(app, 1, 2, 3);
        assertEquals(6, result);
    }

    @Test
    public void testHostApplicativeAsync() throws InterruptedException, ExecutionException {
        HostApplicative app = new HostApplicative(createTestFn(), null, true, null);
        java.util.concurrent.CompletableFuture<Object> future = (java.util.concurrent.CompletableFuture<Object>) Apply.invokeAs(app, 1, 2, 3);
        assertEquals(6, future.get());
    }

    @Test
    public void testApplyAsWithRuntime() {
        I.IContext mockContext = new MockContext();
        HostApplicative app = new HostApplicative(createTestFn(), null, false, mockContext);
        Object result = Apply.applyAs(app, new Object[]{1, 2, 3});
        assertEquals(6, result);
    }

    @Test
    public void testApplyAsWithDefaultRuntime() {
        I.IContext mockContext = new MockContext();
        HostApplicative app = new HostApplicative(createTestFn(), null, false, null) {
            @Override
            public Object applyDefault(Object app) {
                return mockContext;
            }
        };
        Object result = Apply.applyAs(app, new Object[]{1, 2, 3});
        assertEquals(6, result);
    }

    // A mock IContext for testing purposes
    static class MockContext implements I.IContext {
        @Override
        public Object rawEval(Object rt, String string) { return null; }
        @Override
        public Object initPtr(Object rt, Object ptr) { return null; }
        @Override
        public Object tagsPtr(Object rt, Object ptr) { return null; }
        @Override
        public Object derefPtr(Object rt, Object ptr) { return null; }
        @Override
        public Object displayPtr(Object rt, Object ptr) { return null; }
        @Override
        public Object invokePtr(Object rt, Object ptr, Object args) { return null; }
        @Override
        public Object transformInPtr(Object rt, Object ptr, Object args) { return null; }
        @Override
        public Object transformOutPtr(Object rt, Object ptr, Object retrn) { return null; }
    }
}
