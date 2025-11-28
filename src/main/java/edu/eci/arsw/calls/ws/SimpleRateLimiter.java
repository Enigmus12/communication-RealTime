package edu.eci.arsw.calls.ws;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limitador de tasa simple para controlar la cantidad de solicitudes por
 * segundo.
 */
public class SimpleRateLimiter {
    private final int limitPerSecond;
    private AtomicInteger counter = new AtomicInteger(0);
    private long windowEpoch = Instant.now().getEpochSecond();

    /**
     * Crea un limitador de tasa con el límite especificado por segundo.
     * 
     * @param limitPerSecond Límite de solicitudes por segundo.
     */
    public SimpleRateLimiter(int limitPerSecond) {
        this.limitPerSecond = limitPerSecond;
    }

    /**
     * Intenta adquirir un permiso para proceder con una solicitud.
     * 
     * @return true si se adquirió el permiso, false si se excedió el límite.
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
