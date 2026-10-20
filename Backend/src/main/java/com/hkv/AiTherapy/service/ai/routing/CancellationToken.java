package com.hkv.AiTherapy.service.ai.routing;

import java.util.concurrent.atomic.AtomicBoolean;

public final class CancellationToken {
    private final AtomicBoolean cancelled = new AtomicBoolean();

    public boolean cancel() { return cancelled.compareAndSet(false, true); }
    public boolean isCancelled() { return cancelled.get(); }
}
