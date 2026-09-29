package com.pricepulse.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Fixed-window rate limiter keyed by string (client id or IP). In-memory, so it is per backend instance;
 * behind several instances use a shared store (for example Redis) instead. Framework-free.
 */
public final class RateLimiter {
    private static final class Window { long start; int count; }

    private final int limit;
    private final long windowNanos;
    private final LongSupplier nanoClock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private long lastSweep;

    public RateLimiter(int limit, long windowMillis) {
        this(limit, windowMillis, System::nanoTime);
    }

    public RateLimiter(int limit, long windowMillis, LongSupplier nanoClock) {
        this.limit = limit;
        this.windowNanos = windowMillis * 1_000_000L;
        this.nanoClock = nanoClock;
        this.lastSweep = nanoClock.getAsLong();
    }

    /** Returns true if the call is allowed. */
    public boolean tryAcquire(String key) {
        long now = nanoClock.getAsLong();
        sweep(now);
        Window w = windows.computeIfAbsent(key, k -> { Window n = new Window(); n.start = now; return n; });
        synchronized (w) {
            if (now - w.start >= windowNanos) { w.start = now; w.count = 0; }
            if (w.count >= limit) return false;
            w.count++;
            return true;
        }
    }

    /** Seconds until the caller may retry (at least 1). */
    public long retryAfterSeconds(String key) {
        Window w = windows.get(key);
        if (w == null) return 1;
        long remaining = windowNanos - (nanoClock.getAsLong() - w.start);
        return Math.max(1, remaining / 1_000_000_000L + 1);
    }

    private synchronized void sweep(long now) {
        if (now - lastSweep < windowNanos * 2) return;
        lastSweep = now;
        windows.entrySet().removeIf(e -> now - e.getValue().start >= windowNanos * 2); // bounds memory
    }
}
