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

@Service
public class CallSessionService {
    private final CallSessionRepository repo;
    private final MeterRegistry meterRegistry;
    private final QualityMetricsService qualityMetrics;
    private final ULID ulid = new ULID();

    private final AtomicInteger concurrentCalls = new AtomicInteger(0);
    private final long maxMinutes;

    public CallSessionService(CallSessionRepository repo, MeterRegistry meterRegistry,
                              QualityMetricsService qualityMetrics,
                              @Value("${CALL_MAX_MINUTES:60}") long maxMinutes) {
        this.repo = repo;
        this.meterRegistry = meterRegistry;
        this.qualityMetrics = qualityMetrics;
        this.maxMinutes = maxMinutes;
        Gauge.builder("calls.concurrent", concurrentCalls, AtomicInteger::get).register(meterRegistry);
    }

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

    public Optional<CallSession> findBySessionId(String sessionId) {
        return repo.findBySessionId(sessionId);
    }

    public void markConnected(CallSession cs) {
        if (cs.getConnectedAt()==null) cs.setConnectedAt(System.currentTimeMillis());
        cs.setStatus("CONNECTED");
        long setup = cs.getConnectedAt() - cs.getCreatedAt();
        cs.getMetrics().setSetupMs(setup);
        repo.save(cs);
        qualityMetrics.recordSuccess(setup);
    }

    public void markFailedSetup(CallSession cs) {
        qualityMetrics.recordFailure();
        repo.save(cs);
    }

    public void end(CallSession cs) {
        cs.setStatus("ENDED");
        cs.setEndedAt(System.currentTimeMillis());
        if (cs.getConnectedAt()!=null) {
            cs.getMetrics().setTotalDurationMs(cs.getEndedAt() - cs.getConnectedAt());
        }
        repo.save(cs);
        concurrentCalls.decrementAndGet();
    }

    public QualityMetricsService.Snapshot snapshot() {
        return qualityMetrics.snapshot();
    }
}
