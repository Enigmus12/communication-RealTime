package edu.eci.arsw.service;

import edu.eci.arsw.calls.domain.CallSession;
import edu.eci.arsw.calls.domain.CallSessionRepository;
import edu.eci.arsw.calls.service.CallSessionService;
import edu.eci.arsw.calls.service.QualityMetricsService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


class CallSessionServiceTest {

    private CallSessionRepository repo;
    private QualityMetricsService qualityMetrics;
    private SimpleMeterRegistry meterRegistry;
    private CallSessionService service;

    @BeforeEach
    void setUp() {
        repo = mock(CallSessionRepository.class);
        qualityMetrics = mock(QualityMetricsService.class);
        meterRegistry = new SimpleMeterRegistry();
        service = new CallSessionService(repo, meterRegistry, qualityMetrics, 60L);
    }

    // =========================================================================
    // create(String reservationId)
    // =========================================================================

    @Test
    void create_deberiaCrearNuevaSesion_casoFeliz1() {
        String reservationId = "RES-NEW";
        when(repo.findByReservationId(reservationId)).thenReturn(Optional.empty());
        when(repo.save(any(CallSession.class))).thenAnswer(inv -> inv.getArgument(0));

        CallSession cs = service.create(reservationId);

        assertNotNull(cs.getSessionId());
        assertEquals(reservationId, cs.getReservationId());
        assertEquals("CREATED", cs.getStatus());
        assertNotNull(cs.getTtl());
        verify(repo, times(1)).save(cs);

        AtomicInteger cc = (AtomicInteger) ReflectionTestUtils.getField(service, "concurrentCalls");
        assertNotNull(cc);
        assertEquals(1, cc.get());
    }

    @Test
    void create_deberiaReusarSesionExistente_casoFeliz2() {
        String reservationId = "RES-EXIST";
        CallSession existing = CallSession.create("SESSION-EX", reservationId, Instant.now());
        when(repo.findByReservationId(reservationId)).thenReturn(Optional.of(existing));

        CallSession cs = service.create(reservationId);

        assertSame(existing, cs);
        verify(repo, never()).save(any());

        AtomicInteger cc = (AtomicInteger) ReflectionTestUtils.getField(service, "concurrentCalls");
        assertNotNull(cc);
        assertEquals(0, cc.get());
    }

    @Test
    void create_noDeberiaPasar_cuandoRepoFindLanzaExcepcion() {
        String reservationId = "RES-ERR";
        when(repo.findByReservationId(reservationId))
                .thenThrow(new RuntimeException("DB error"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.create(reservationId));

        assertEquals("DB error", ex.getMessage());
    }

    @Test
    void create_noDeberiaPasar_cuandoRepoSaveFalla() {
        String reservationId = "RES-SAVE-ERR";
        when(repo.findByReservationId(reservationId)).thenReturn(Optional.empty());
        when(repo.save(any(CallSession.class)))
                .thenThrow(new RuntimeException("Save error"));

        AtomicInteger ccBefore = (AtomicInteger) ReflectionTestUtils.getField(service, "concurrentCalls");
        assertNotNull(ccBefore);
        assertEquals(0, ccBefore.get());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.create(reservationId));

        assertEquals("Save error", ex.getMessage());

        AtomicInteger ccAfter = (AtomicInteger) ReflectionTestUtils.getField(service, "concurrentCalls");
        assertEquals(0, ccAfter.get()); // no debe incrementarse si falla
    }

    // =========================================================================
    // findBySessionId(String sessionId)
    // =========================================================================

    @Test
    void findBySessionId_deberiaRetornarSesion_casoFeliz1() {
        String sessionId = "SESSION-1";
        CallSession cs = CallSession.create(sessionId, "RES-1", Instant.now());
        when(repo.findBySessionId(sessionId)).thenReturn(Optional.of(cs));

        Optional<CallSession> result = service.findBySessionId(sessionId);

        assertTrue(result.isPresent());
        assertEquals(sessionId, result.get().getSessionId());
    }

    @Test
    void findBySessionId_deberiaRetornarVacio_casoFeliz2() {
        when(repo.findBySessionId("NO-EXIST")).thenReturn(Optional.empty());

        Optional<CallSession> result = service.findBySessionId("NO-EXIST");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void findBySessionId_noDeberiaPasar_cuandoRepoLanzaExcepcion() {
        when(repo.findBySessionId("ERR"))
                .thenThrow(new RuntimeException("Repo error"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.findBySessionId("ERR"));

        assertEquals("Repo error", ex.getMessage());
    }

    @Test
    void findBySessionId_noDeberiaPasar_cuandoRepoRetornaNull() {
        when(repo.findBySessionId("BAD")).thenReturn(null); // repositorio mal comportado

        Optional<CallSession> result = service.findBySessionId("BAD");

        assertNull(result); // escenario no deseado, pero lo documentamos
    }

    // =========================================================================
    // markConnected(CallSession cs)
    // =========================================================================

    @Test
    void markConnected_deberiaMarcarConectadaPrimeraVez_casoFeliz1() {
        CallSession cs = CallSession.create("S-1", "R-1", Instant.now());
        cs.setCreatedAt(System.currentTimeMillis() - 100);
        when(repo.save(cs)).thenReturn(cs);

        service.markConnected(cs);

        assertEquals("CONNECTED", cs.getStatus());
        assertNotNull(cs.getConnectedAt());
        assertTrue(cs.getMetrics().getSetupMs() >= 0);
        verify(repo, times(1)).save(cs);
        verify(qualityMetrics, times(1))
                .recordSuccess(cs.getMetrics().getSetupMs());
    }

    @Test
    void markConnected_deberiaRespetarConnectedAtExistente_casoFeliz2() {
        CallSession cs = CallSession.create("S-2", "R-2", Instant.now());
        long createdAt = System.currentTimeMillis() - 200;
        long connectedAt = createdAt + 50;
        cs.setCreatedAt(createdAt);
        cs.setConnectedAt(connectedAt);
        when(repo.save(cs)).thenReturn(cs);

        service.markConnected(cs);

        assertEquals("CONNECTED", cs.getStatus());
        assertEquals(connectedAt, cs.getConnectedAt());
        assertEquals(50L, cs.getMetrics().getSetupMs(), 5L);
        verify(qualityMetrics, times(1))
                .recordSuccess(cs.getMetrics().getSetupMs());
    }

    @Test
    void markConnected_noDeberiaPasar_cuandoSesionEsNull() {
        assertThrows(NullPointerException.class,
                () -> service.markConnected(null));
    }

    @Test
    void markConnected_noDeberiaPasar_cuandoRepoSaveFalla() {
        CallSession cs = CallSession.create("S-ERR", "R-ERR", Instant.now());
        cs.setCreatedAt(System.currentTimeMillis() - 100);
        when(repo.save(cs))
                .thenThrow(new RuntimeException("Save error"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.markConnected(cs));

        assertEquals("Save error", ex.getMessage());
        verify(qualityMetrics, never()).recordSuccess(anyLong());
    }

    // =========================================================================
    // markFailedSetup(CallSession cs)
    // =========================================================================

    @Test
    void markFailedSetup_deberiaRegistrarFallo_casoFeliz1() {
        CallSession cs = CallSession.create("S-F1", "R-F1", Instant.now());
        when(repo.save(cs)).thenReturn(cs);

        service.markFailedSetup(cs);

        verify(qualityMetrics, times(1)).recordFailure();
        verify(repo, times(1)).save(cs);
    }

    @Test
    void markFailedSetup_deberiaFuncionarAunqueMetricsSeaNull_casoFeliz2() {
        CallSession cs = CallSession.create("S-F2", "R-F2", Instant.now());
        cs.setMetrics(null);
        when(repo.save(cs)).thenReturn(cs);

        service.markFailedSetup(cs);

        verify(qualityMetrics, times(1)).recordFailure();
        verify(repo, times(1)).save(cs);
    }

    @Test
    void markFailedSetup_noDeberiaPasar_cuandoRepoSaveFalla() {
        CallSession cs = CallSession.create("S-F3", "R-F3", Instant.now());
        doThrow(new RuntimeException("Save error"))
                .when(repo).save(cs);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.markFailedSetup(cs));

        assertEquals("Save error", ex.getMessage());
        verify(qualityMetrics, times(1)).recordFailure();
    }

    @Test
    void markFailedSetup_noDeberiaPasar_cuandoRecordFailureFalla() {
        CallSession cs = CallSession.create("S-F4", "R-F4", Instant.now());
        doThrow(new RuntimeException("metrics error"))
                .when(qualityMetrics).recordFailure();

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.markFailedSetup(cs));

        assertEquals("metrics error", ex.getMessage());
        verify(repo, never()).save(any());
    }

    // =========================================================================
    // end(CallSession cs)
    // =========================================================================

    @Test
    void end_deberiaCerrarSesionConDuracion_casoFeliz1() {
        CallSession cs = CallSession.create("S-END1", "R-END1", Instant.now());
        long connectedAt = System.currentTimeMillis() - 500;
        cs.setConnectedAt(connectedAt);
        when(repo.save(cs)).thenReturn(cs);

        ReflectionTestUtils.setField(service, "concurrentCalls", new AtomicInteger(1));

        service.end(cs);

        assertEquals("ENDED", cs.getStatus());
        assertNotNull(cs.getEndedAt());
        assertTrue(cs.getMetrics().getTotalDurationMs() > 0);

        AtomicInteger cc = (AtomicInteger) ReflectionTestUtils.getField(service, "concurrentCalls");
        assertEquals(0, cc.get());
        verify(repo, times(1)).save(cs);
    }

    @Test
    void end_deberiaCerrarSesionSinDuracion_cuandoNoHuboConexion_casoFeliz2() {
        CallSession cs = CallSession.create("S-END2", "R-END2", Instant.now());
        when(repo.save(cs)).thenReturn(cs);
        ReflectionTestUtils.setField(service, "concurrentCalls", new AtomicInteger(1));

        service.end(cs);

        assertEquals("ENDED", cs.getStatus());
        assertNotNull(cs.getEndedAt());
        assertEquals(0, cs.getMetrics().getTotalDurationMs());

        AtomicInteger cc = (AtomicInteger) ReflectionTestUtils.getField(service, "concurrentCalls");
        assertEquals(0, cc.get());
    }

    @Test
    void end_noDeberiaPasar_cuandoSesionEsNull() {
        assertThrows(NullPointerException.class,
                () -> service.end(null));
    }

    @Test
    void end_noDeberiaPasar_cuandoRepoSaveFalla_yNoDecrementaContador() {
        CallSession cs = CallSession.create("S-END3", "R-END3", Instant.now());
        ReflectionTestUtils.setField(service, "concurrentCalls", new AtomicInteger(3));

        doThrow(new RuntimeException("Save error"))
                .when(repo).save(cs);

        AtomicInteger ccBefore = (AtomicInteger) ReflectionTestUtils.getField(service, "concurrentCalls");
        assertEquals(3, ccBefore.get());

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.end(cs));

        assertEquals("Save error", ex.getMessage());

        AtomicInteger ccAfter = (AtomicInteger) ReflectionTestUtils.getField(service, "concurrentCalls");
        assertEquals(3, ccAfter.get()); // no debe decrementar si falla save
    }

    // =========================================================================
    // snapshot()
    // =========================================================================

    @Test
    void snapshot_deberiaRetornarSnapshotDelServicio_casoFeliz1() {
        QualityMetricsService.Snapshot snap =
                new QualityMetricsService.Snapshot(10L, 20L, 0.8, 5);
        when(qualityMetrics.snapshot()).thenReturn(snap);

        QualityMetricsService.Snapshot result = service.snapshot();

        assertSame(snap, result);
    }

    @Test
    void snapshot_deberiaLlamarQualityMetrics_casoFeliz2() {
        when(qualityMetrics.snapshot())
                .thenReturn(new QualityMetricsService.Snapshot(0L, 0L, 1.0, 0));

        service.snapshot();

        verify(qualityMetrics, times(1)).snapshot();
    }

    @Test
    void snapshot_noDeberiaPasar_cuandoQualityMetricsLanzaExcepcion() {
        when(qualityMetrics.snapshot())
                .thenThrow(new RuntimeException("metrics error"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.snapshot());

        assertEquals("metrics error", ex.getMessage());
    }

    @Test
    void snapshot_noDeberiaPasar_cuandoRetornaNull() {
        when(qualityMetrics.snapshot()).thenReturn(null);

        QualityMetricsService.Snapshot result = service.snapshot();

        assertNull(result); // escenario raro, lo dejamos documentado
    }
}
