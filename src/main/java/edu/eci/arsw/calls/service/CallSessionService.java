package edu.eci.arsw.calls.service;

import de.huxhorn.sulky.ulid.ULID;
import edu.eci.arsw.calls.domain.*;
import io.micrometer.core.instrument.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Servicio para gestionar las sesiones de llamadas.
 */
@Service
public class CallSessionService {
    private final CallSessionRepository repo;
    private final QualityMetricsService qualityMetrics;
    private final ULID ulid = new ULID();

    private final AtomicInteger concurrentCalls = new AtomicInteger(0);
    private final long maxMinutes;

    public CallSessionService(CallSessionRepository repo, MeterRegistry meterRegistry, 
            QualityMetricsService qualityMetrics,
            @Value("${CALL_MAX_MINUTES:60}") long maxMinutes) {
        this.repo = repo;
        this.qualityMetrics = qualityMetrics;
        this.maxMinutes = maxMinutes;
        Gauge.builder("calls.concurrent", concurrentCalls, AtomicInteger::get).register(meterRegistry);
    }

    /**
     * Crea una nueva sesión de llamada o devuelve una existente para la reserva
     * dada.
     * * @param reservationId ID de la reserva.
     * @return La sesión de llamada creada o existente.
     */
    @Transactional
    public CallSession create(String reservationId) {
        Optional<CallSession> existing = repo.findByReservationId(reservationId);
        if (existing.isPresent())
            return existing.get();
        String sessionId = ulid.nextULID();
        Instant ttl = Instant.now().plus(maxMinutes + 10, ChronoUnit.MINUTES);
        CallSession cs = CallSession.create(sessionId, reservationId, ttl);
        repo.save(cs);
        concurrentCalls.incrementAndGet();
        return cs;
    }

    /**
     * Busca una sesión de llamada por su ID de sesión.
     * * @param sessionId ID de la sesión.
     * @return La sesión de llamada si existe.
     */
    public Optional<CallSession> findBySessionId(String sessionId) {
        return repo.findBySessionId(sessionId);
    }

    /**
     * Marca una sesión de llamada como conectada.
     * * @param cs La sesión de llamada.
     */
    public void markConnected(CallSession cs) {
        if (cs.getConnectedAt() == null)
            cs.setConnectedAt(System.currentTimeMillis());
        cs.setStatus("CONNECTED");
        long setup = cs.getConnectedAt() - cs.getCreatedAt();
        cs.getMetrics().setSetupMs(setup);
        repo.save(cs);
        qualityMetrics.recordSuccess(setup);
    }

    /**
     * Marca una sesión de llamada como fallida en la configuración.
     * * @param cs La sesión de llamada.
     */
    public void markFailedSetup(CallSession cs) {
        qualityMetrics.recordFailure();
        repo.save(cs);
    }

    /**
     * Marca una sesión de llamada como terminada.
     * * @param cs La sesión de llamada.
     */
    public void end(CallSession cs) {
        cs.setStatus("ENDED");
        cs.setEndedAt(System.currentTimeMillis());
        if (cs.getConnectedAt() != null) {
            cs.getMetrics().setTotalDurationMs(cs.getEndedAt() - cs.getConnectedAt());
        }
        repo.save(cs);
        concurrentCalls.decrementAndGet();
    }

    /**
     * Obtiene una instantánea de las métricas de calidad.
     * * @return Instantánea de las métricas de calidad.
     */
    public QualityMetricsService.Snapshot snapshot() {
        return qualityMetrics.snapshot();
    }
}