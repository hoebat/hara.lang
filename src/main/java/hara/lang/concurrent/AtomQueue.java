package hara.lang.concurrent;

import hara.lang.data.Atom;
import hara.lang.data.Vector;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import java.util.concurrent.atomic.AtomicBoolean;

public class AtomQueue<T> {

    private final Atom.Swap<Atom, Vector<T>> queue;
    private final ExecutorService executor;
    private final Consumer<Vector<T>> handler;
    private final int maxBatch;
    private final AtomicBoolean processing = new AtomicBoolean(false);

    public AtomQueue(ExecutorService executor, Consumer<Vector<T>> handler, int maxBatch) {
        this.queue = new Atom.Basic<Vector<T>>(Vector.Standard.empty(null));
        this.executor = executor;
        this.handler = handler;
        this.maxBatch = maxBatch;
    }

    public void add(T item) {
        queue.swap(q -> (Vector<T>)q.conj(item));
        processQueue();
    }

    @SuppressWarnings("unchecked")
    public void addAll(T... items) {
        queue.swap(q -> {
            Vector<T> newQueue = q;
            for (T item : items) {
                newQueue = (Vector<T>)newQueue.conj(item);
            }
            return newQueue;
        });
        processQueue();
    }

    private void processQueue() {
        if (processing.compareAndSet(false, true)) {
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    Vector<T> currentQueue = (Vector<T>) queue.swap(q -> Vector.Standard.empty(null));
                    if (currentQueue.count() > 0) {
                        Vector.Standard<T> batch = (Vector.Standard<T>) currentQueue;
                        for (int i = 0; i < batch.count(); i += maxBatch) {
                            int end = Math.min(i + maxBatch, (int) batch.count());
                            handler.accept(batch.subview(i, end));
                        }
                    }
                } finally {
                    processing.set(false);
                }
            }, executor);
        }
    }
}
