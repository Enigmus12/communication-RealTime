package edu.eci.arsw.calls.service;

import de.huxhorn.sulky.ulid.ULID;
import edu.eci.arsw.calls.domain.CallSession;
import edu.eci.arsw.calls.domain.CallSessionRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Servicio para gestionar las sesiones de llamada
 */
@Service
public class CallSessionService {
    private final CallSessionRepository repo;
    private final MeterRegistry meterRegistry;
    private final ULID ulid = new ULID();

    private final AtomicInteger concurrentCalls = new AtomicInteger(0);

    private final long maxMinutes;

    public CallSessionService(CallSessionRepository repo, MeterRegistry meterRegistry,
                              @Value("${CALL_MAX_MINUTES:60}") long maxMinutes) {
        this.repo = repo;
        this.meterRegistry = meterRegistry;
        this.maxMinutes = maxMinutes;
        Gauge.builder("calls.concurrent", concurrentCalls, AtomicInteger::get).register(meterRegistry);
    }

    /**
     * Crea una nueva sesión de llamada
     */
    @Transactional
    public CallSession create(String reservationId) {
        Optional<CallSession> existing = repo.findByReservationId(reservationId);
        if (existing.isPresent()) return existing.get();
        String sessionId = ulid.nextULID();
        Instant ttl = Instant.now().plus(maxMinutes + 10, ChronoUnit.MINUTES);
        CallSession cs = CallSession.create(sessionId, reservationId, ttl);
        repo.save(cs);
        concurrentCalls.incrementAndGet();
        return cs;
    }

    /**
     * Busca una sesión de llamada por su ID de sesión
     */
    public Optional<CallSession> findBySessionId(String sessionId) {
        return repo.findBySessionId(sessionId);
    }

    /**
     * Marca una sesión de llamada como conectada
     */
    public void markConnected(CallSession cs) {
        if (cs.getConnectedAt()==null) cs.setConnectedAt(System.currentTimeMillis());
        cs.setStatus("CONNECTED");
        repo.save(cs);
    }

    /**
     * Finaliza una sesión de llamada
     */
    public void end(CallSession cs) {
        cs.setStatus("ENDED");
        cs.setEndedAt(System.currentTimeMillis());
        repo.save(cs);
        concurrentCalls.decrementAndGet();
    }
}
