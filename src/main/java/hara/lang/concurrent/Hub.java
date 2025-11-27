package hara.lang.concurrent;

import hara.lang.base.Std;
import hara.lang.data.Atom;
import hara.lang.data.Vector;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

import java.util.concurrent.atomic.AtomicBoolean;

import java.util.concurrent.atomic.AtomicBoolean;

public class Hub<T> {

    private static class HubState<T> {
        final CompletableFuture<Void> ticket;
        final Vector<T> queue;

        HubState(CompletableFuture<Void> ticket, Vector<T> queue) {
            this.ticket = ticket;
            this.queue = queue;
        }
    }

    private final Atom.Swap<Atom, HubState<T>> hub;
    private final ExecutorService executor;
    private final Consumer<Vector<T>> handler;
    private final int maxBatch;
    private final AtomicBoolean processing = new AtomicBoolean(false);

    public Hub(ExecutorService executor, Consumer<Vector<T>> handler, int maxBatch) {
        this.hub = new Atom.Basic<>(new HubState<>(new CompletableFuture<>(), Vector.Standard.empty(null)));
        this.executor = executor;
        this.handler = handler;
        this.maxBatch = maxBatch;
    }

    public void addAll(T... items) {
        hub.swap(state -> {
            Vector<T> newQueue = state.queue;
            for (T item : items) {
                newQueue = (Vector<T>) newQueue.conj(item);
            }
            return new HubState<>(state.ticket, newQueue);
        });
    }

    public void processQueue() {
        if (processing.compareAndSet(false, true)) {
            CompletableFuture.runAsync(() -> {
                try {
                    HubState<T> oldState = (HubState<T>) hub.swap(s -> new HubState<>(new CompletableFuture<>(), Vector.Standard.empty(null)));
                    if (oldState.queue.count() > 0) {
                        Vector.Standard<T> batch = (Vector.Standard<T>) oldState.queue;
                        for (int i = 0; i < batch.count(); i += maxBatch) {
                            int end = Math.min(i + maxBatch, (int) batch.count());
                            handler.accept(batch.subview(i, end));
                        }
                        oldState.ticket.complete(null);
                    }
                } finally {
                    processing.set(false);
                }
            }, executor);
        }
    }

    public CompletableFuture<Void> waitFuture() {
        return hub.deref().ticket;
    }

    public void waitResult() {
        hub.deref().ticket.join();
    }
}
