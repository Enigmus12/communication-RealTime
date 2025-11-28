package edu.eci.arsw.service;

import edu.eci.arsw.calls.domain.CallSession;
import edu.eci.arsw.calls.domain.CallSessionRepository;
import edu.eci.arsw.calls.service.CallSessionService;
import edu.eci.arsw.calls.service.CleanupScheduler;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;

import static org.mockito.Mockito.*;

class CleanupSchedulerTest {

    @Test
    void autoEndOldSessions_deberiaFinalizarSesionesVencidas_casoFeliz1() {
        CallSessionRepository repo = mock(CallSessionRepository.class);
        CallSessionService service = mock(CallSessionService.class);

        CallSession oldSession = CallSession.create("S-OLD", "R1", Instant.now());
        oldSession.setCreatedAt(System.currentTimeMillis() - 2 * 60 * 60 * 1000L); // 2h atrás
        oldSession.setStatus("CREATED");

        CallSession fresh = CallSession.create("S-NEW", "R2", Instant.now());
        fresh.setCreatedAt(System.currentTimeMillis());
        fresh.setStatus("CREATED");

        when(repo.findAll()).thenReturn(List.of(oldSession, fresh));

        CleanupScheduler scheduler = new CleanupScheduler(repo, service, 60L);
        scheduler.autoEndOldSessions();

        verify(service).end(oldSession);
        verify(service, never()).end(fresh);
    }

    @Test
    void autoEndOldSessions_noDeberiaFinalizarSesionesYaTerminadas_casoFeliz2() {
        CallSessionRepository repo = mock(CallSessionRepository.class);
        CallSessionService service = mock(CallSessionService.class);

        CallSession ended = CallSession.create("S-END", "R1", Instant.now());
        ended.setCreatedAt(System.currentTimeMillis() - 3 * 60 * 60 * 1000L);
        ended.setStatus("ENDED");

        when(repo.findAll()).thenReturn(List.of(ended));

        CleanupScheduler scheduler = new CleanupScheduler(repo, service, 60L);
        scheduler.autoEndOldSessions();

        verify(service, never()).end(any());
    }

    @Test
    void autoEndOldSessions_deberiaIgnorarListaVacia() {
        CallSessionRepository repo = mock(CallSessionRepository.class);
        CallSessionService service = mock(CallSessionService.class);

        when(repo.findAll()).thenReturn(List.of());

        CleanupScheduler scheduler = new CleanupScheduler(repo, service, 60L);
        scheduler.autoEndOldSessions();

        verifyNoInteractions(service);
    }

    @Test
    void autoEndOldSessions_deberiaUsarMaxMinutesConfigurado_casoBorde() {
        CallSessionRepository repo = mock(CallSessionRepository.class);
        CallSessionService service = mock(CallSessionService.class);

        CallSession borderline = CallSession.create("S-B", "R1", Instant.now());
        long now = System.currentTimeMillis();
        borderline.setCreatedAt(now - 30 * 60 * 1000L); // 30 min

        when(repo.findAll()).thenReturn(List.of(borderline));

        CleanupScheduler scheduler = new CleanupScheduler(repo, service, 60L); // límite 60 min
        scheduler.autoEndOldSessions();

        verify(service, never()).end(any());
    }
}
