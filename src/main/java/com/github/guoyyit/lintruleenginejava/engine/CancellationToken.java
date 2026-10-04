package com.github.guoyyit.lintruleenginejava.engine;

/** Simple cooperative cancellation token. */
public final class CancellationToken {

    private volatile boolean cancelled;

    public void cancel() {
        this.cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public static CancellationToken none() {
        return new CancellationToken();
    }
}
