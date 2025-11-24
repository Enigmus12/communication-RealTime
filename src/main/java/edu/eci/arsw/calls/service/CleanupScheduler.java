package edu.eci.arsw.calls.service;

import edu.eci.arsw.calls.domain.CallSession;
import edu.eci.arsw.calls.domain.CallSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler para limpiar sesiones de llamada antiguas
 */
@Component
public class CleanupScheduler {
    private static final Logger log = LoggerFactory.getLogger(CleanupScheduler.class);

    private final CallSessionRepository repo;
    private final CallSessionService service;
    private final long maxMinutesMs;
    /**
     * Constructor del scheduler de limpieza
     * @param repo Repositorio de sesiones de llamada
     * @param service Servicio de sesiones de llamada
     * @param maxMinutes Duración máxima de una llamada en minutos
     */
    public CleanupScheduler(CallSessionRepository repo, CallSessionService service,
                            @Value("${CALL_MAX_MINUTES:60}") long maxMinutes) {
        this.repo = repo;
        this.service = service;
        this.maxMinutesMs = maxMinutes * 60_000L;
    }
    /**
     * Tarea programada para finalizar sesiones de llamada antiguas
     */
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
