package hara.lang.concurrent;

import hara.lang.data.Vector;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class HubTest {

    @Test
    public void testHub() throws InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Vector<Integer>> result = new AtomicReference<>(Vector.Standard.empty(null));

        Hub<Integer> hub = new Hub<>(executor,
                items -> {
                    Vector<Integer> current = result.get();
                    for (Object item : items) {
                        current = (Vector<Integer>) current.conj((Integer)item);
                    }
                    result.set(current);
                }, 10);

        hub.addAll(1, 2, 3, 4);
        hub.processQueue();

        hub.waitResult();

        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        assertEquals(Vector.Standard.from(null, 1, 2, 3, 4), result.get());
    }
}
