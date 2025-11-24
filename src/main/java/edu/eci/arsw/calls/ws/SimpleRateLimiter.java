package edu.eci.arsw.calls.ws;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limitador de tasa simple para controlar la cantidad de solicitudes por segundo
 */
public class SimpleRateLimiter {
    private final int limitPerSecond;
    private AtomicInteger counter = new AtomicInteger(0);
    private long windowEpoch = Instant.now().getEpochSecond();
    /**
     * Constructor del limitador de tasa
     * @param limitPerSecond Límite de solicitudes por segundo
     */
    public SimpleRateLimiter(int limitPerSecond) {
        this.limitPerSecond = limitPerSecond;
    }

    /**
     * Intenta adquirir un permiso para realizar una solicitud
     * @return true si se permite la solicitud, false si se excedió el límite
     */
    public synchronized boolean tryAcquire() {
        long now = Instant.now().getEpochSecond();
        if (now != windowEpoch) {
            windowEpoch = now;
            counter.set(0);
        }
        return counter.incrementAndGet() <= limitPerSecond;
    }
}
