package com.hkv.AiTherapy.service.ai.routing;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.ArrayList;
import java.util.List;

public final class CancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final Object monitor = new Object();
    private final List<Runnable> callbacks = new ArrayList<>();

    public boolean cancel() {
        List<Runnable> toRun;
        synchronized (monitor) {
            if (!cancelled.compareAndSet(false, true)) return false;
            toRun = List.copyOf(callbacks);
            callbacks.clear();
        }
        toRun.forEach(CancellationToken::runSafely);
        return true;
    }
    public boolean isCancelled() { return cancelled.get(); }

    /** Registers a best-effort cancellation action and returns a handle that removes it. */
    public AutoCloseable onCancel(Runnable callback) {
        if (callback == null) throw new IllegalArgumentException("Cancellation callback is required");
        synchronized (monitor) {
            if (!cancelled.get()) {
                callbacks.add(callback);
                return () -> { synchronized (monitor) { callbacks.remove(callback); } };
            }
        }
        runSafely(callback);
        return () -> { };
    }

    private static void runSafely(Runnable callback) {
        try { callback.run(); } catch (RuntimeException ignored) { }
    }
}
