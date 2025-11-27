package hara.lang.concurrent;

import hara.lang.data.Vector;
import org.junit.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.Executors;

import java.util.concurrent.CountDownLatch;

public class AtomQueueTest {

    @Test
    public void testAtomQueue() throws InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        AtomicReference<Vector<Integer>> result = new AtomicReference<>(Vector.Standard.empty(null));
        CountDownLatch latch = new CountDownLatch(1);

        AtomQueue<Integer> atomQueue = new AtomQueue<>(executor,
                items -> {
                    synchronized (result) {
                        Vector<Integer> newV = result.get();
                        for (Object item : items) {
                            newV = (Vector<Integer>) newV.conj((Integer)item);
                        }
                        result.set(newV);
                    }
                    latch.countDown();
                }, 10);

        atomQueue.add(1);
        atomQueue.addAll(2, 3, 4);

        latch.await(5, TimeUnit.SECONDS);

        assertEquals(Vector.Standard.from(null, 1, 2, 3, 4), result.get());
        executor.shutdown();
    }
}
