package edu.eci.arsw.calls.ws;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

public class SimpleRateLimiter {
    private final int limitPerSecond;
    private AtomicInteger counter = new AtomicInteger(0);
    private long windowEpoch = Instant.now().getEpochSecond();

    public SimpleRateLimiter(int limitPerSecond) { this.limitPerSecond = limitPerSecond; }

    public synchronized boolean tryAcquire() {
        long now = Instant.now().getEpochSecond();
        if (now != windowEpoch) { windowEpoch = now; counter.set(0); }
        return counter.incrementAndGet() <= limitPerSecond;
    }
}
