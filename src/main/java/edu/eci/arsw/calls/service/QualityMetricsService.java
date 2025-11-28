package edu.eci.arsw.calls.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Servicio para recopilar y proporcionar métricas de calidad de las llamadas.
 */
@Service
public class QualityMetricsService {

    private static final long WINDOW_MS = 5 * 60_000L;

    private final Timer setupTimer;
    private final Counter successCounter;
    private final Counter failCounter;

    private final ConcurrentLinkedQueue<Sample> samples = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Long> successes = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Long> failures = new ConcurrentLinkedQueue<>();

    record Sample(long ts, long setupMs) {
    }

    public QualityMetricsService(MeterRegistry registry) {
        this.setupTimer = Timer.builder("call.setup.ms")
                .publishPercentiles(0.95, 0.99)
                .publishPercentileHistogram(true)
                .sla(
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(4),
                        Duration.ofSeconds(6),
                        Duration.ofSeconds(10))
                .register(registry);

        this.successCounter = Counter.builder("call.setup.success").register(registry);
        this.failCounter = Counter.builder("call.setup.fail").register(registry);
    }

    /*
     * Registra una llamada exitosa con el tiempo de configuración dado.
     * 
     * @param setupMs Tiempo de configuración en milisegundos.
     */
    public void recordSuccess(long setupMs) {
        setupTimer.record(setupMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        samples.add(new Sample(System.currentTimeMillis(), setupMs));
        successes.add(System.currentTimeMillis());
        evictOld();
        successCounter.increment();
    }

    /*
     * Registra una llamada fallida.
     */
    public void recordFailure() {
        failures.add(System.currentTimeMillis());
        evictOld();
        failCounter.increment();
    }

    /*
     * Proporciona una instantánea de las métricas actuales.
     * 
     * @return Instantánea de las métricas.
     */
    public Snapshot snapshot() {
        evictOld();
        List<Long> window = samples.stream().map(Sample::setupMs).sorted().toList();
        double p95 = percentile(window, 0.95);
        double p99 = percentile(window, 0.99);
        long now = System.currentTimeMillis();
        long s = successes.stream().filter(t -> now - t <= WINDOW_MS).count();
        long f = failures.stream().filter(t -> now - t <= WINDOW_MS).count();
        double successRate = (s + f) == 0 ? 1.0 : (double) s / (double) (s + f);
        return new Snapshot((long) p95, (long) p99, successRate, window.size());
    }

    /*
     * Elimina muestras antiguas fuera de la ventana de tiempo.
     */
    private void evictOld() {
        long now = System.currentTimeMillis();
        while (!samples.isEmpty() && now - samples.peek().ts() > WINDOW_MS)
            samples.poll();
        while (!successes.isEmpty() && now - successes.peek() > WINDOW_MS)
            successes.poll();
        while (!failures.isEmpty() && now - failures.peek() > WINDOW_MS)
            failures.poll();
    }

    /*
     * Calcula el percentil dado de una lista ordenada.
     * 
     * @param sorted Lista ordenada de valores.
     * 
     * @param q Cuantil deseado (por ejemplo, 0.95 para el percentil 95).
     * 
     * @return Valor del percentil.
     */
    private static double percentile(List<Long> sorted, double q) {
        if (sorted.isEmpty())
            return 0;
        int idx = (int) Math.ceil(q * sorted.size()) - 1;
        idx = Math.max(0, Math.min(idx, sorted.size() - 1));
        return sorted.get(idx);
    }

    /*
     * Registro de una instantánea de las métricas de calidad.
     */
    public record Snapshot(long p95ms, long p99ms, double successRate, int samples) {
    }
}
