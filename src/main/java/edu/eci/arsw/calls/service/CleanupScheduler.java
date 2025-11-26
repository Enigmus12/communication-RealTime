package edu.eci.arsw.calls.service;

import edu.eci.arsw.calls.domain.CallSession;
import edu.eci.arsw.calls.domain.CallSessionRepository;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

@Component
public class CleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(CleanupScheduler.class);

    private final CallSessionRepository repo;
    private final CallSessionService service;
    private final long maxMinutesMs;

    public CleanupScheduler(CallSessionRepository repo, CallSessionService service,
                            @Value("${CALL_MAX_MINUTES:60}") long maxMinutes) {
        this.repo = repo; this.service = service;
        this.maxMinutesMs = maxMinutes * 60_000L;
    }

    @Scheduled(fixedDelay = 30000)
    public void autoEndOldSessions() {
        long now = System.currentTimeMillis();
        for (CallSession cs : repo.findAll()) {
            if (!"ENDED".equals(cs.getStatus()) && (now - cs.getCreatedAt()) > maxMinutesMs) {
                log.info("Auto END sessionId={} by scheduler", cs.getSessionId());
                service.end(cs);
            }
        }
    }
}
