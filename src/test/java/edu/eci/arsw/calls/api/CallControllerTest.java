package edu.eci.arsw.calls.api;

import edu.eci.arsw.calls.domain.CallSession;
import edu.eci.arsw.calls.service.CallSessionService;
import edu.eci.arsw.calls.service.QualityMetricsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CallControllerTest {

    @Mock
    private CallSessionService callService;

    @Mock
    private QualityMetricsService qualityMetricsService;

    @InjectMocks
    private CallController controller;

    @BeforeEach
    void setUp() {
        // Valores por defecto para STUN/TURN en los tests
        ReflectionTestUtils.setField(controller, "stunUrlsCsv", "stun:stun1,stun:stun2");
        ReflectionTestUtils.setField(controller, "turnUrlsCsv", "turn:turn1,turn:turn2");
        ReflectionTestUtils.setField(controller, "turnUser", "turnUser");
        ReflectionTestUtils.setField(controller, "turnPass", "turnPass");
    }

    // -------------------------------------------------------------------------
    // createSession(Map<String,String> req, Authentication auth)
    // -------------------------------------------------------------------------

    @Test
    void createSession_deberiaCrearSesionCorrectamente_casoFeliz1() {
        String reservationId = "RES-1";
        CallSession cs = CallSession.create("SESSION-1", reservationId,
                Instant.now().plus(70, ChronoUnit.MINUTES));
        when(callService.create(reservationId)).thenReturn(cs);

        Map<String, String> req = Map.of("reservationId", reservationId);

        Map<String, Object> resp = controller.createSession(req, null);

        assertEquals("SESSION-1", resp.get("sessionId"));
        assertEquals("RES-1", resp.get("reservationId"));
        assertEquals(60 * 70, resp.get("ttlSeconds"));
        verify(callService, times(1)).create(reservationId);
    }

    @Test
    void createSession_deberiaReutilizarSesionExistente_casoFeliz2() {
        String reservationId = "RES-2";
        CallSession existing = CallSession.create("SESSION-EXIST", reservationId,
                Instant.now().plus(70, ChronoUnit.MINUTES));
        when(callService.create(reservationId)).thenReturn(existing);

        Map<String, String> req = Map.of("reservationId", reservationId);

        Map<String, Object> resp = controller.createSession(req, null);

        assertEquals("SESSION-EXIST", resp.get("sessionId"));
        verify(callService, times(1)).create(reservationId);
    }

    @Test
    void createSession_noDeberiaPasar_cuandoServicioLanzaExcepcion() {
        String reservationId = "RES-ERROR";
        Map<String, String> req = Map.of("reservationId", reservationId);

        when(callService.create(reservationId))
                .thenThrow(new RuntimeException("DB error"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> controller.createSession(req, null));

        assertEquals("DB error", ex.getMessage());
    }

    @Test
    void createSession_noDeberiaPasar_cuandoFaltaReservationId() {
        Map<String, String> req = Map.of(); // sin reservationId

        assertThrows(NullPointerException.class,
                () -> controller.createSession(req, null));

        verify(callService, times(1)).create(null);
    }

    // -------------------------------------------------------------------------
    // end(String sessionId)
    // -------------------------------------------------------------------------

    @Test
    void end_deberiaTerminarSesionExistente_casoFeliz1() {
        String sessionId = "SESSION-OK";
        CallSession cs = CallSession.create(sessionId, "RES-OK",
                Instant.now().plus(70, ChronoUnit.MINUTES));
        when(callService.findBySessionId(sessionId)).thenReturn(Optional.of(cs));

        ResponseEntity<Void> resp = controller.end(sessionId);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        verify(callService, times(1)).end(cs);
    }

    @Test
    void end_deberiaLlamarEndUnaSolaVez_casoFeliz2() {
        String sessionId = "SESSION-2";
        CallSession cs = CallSession.create(sessionId, "RES-2",
                Instant.now().plus(70, ChronoUnit.MINUTES));
        when(callService.findBySessionId(sessionId)).thenReturn(Optional.of(cs));

        controller.end(sessionId);

        verify(callService, times(1)).end(cs);
    }

    @Test
    void end_noDeberiaPasar_cuandoSesionNoExiste() {
        String sessionId = "SESSION-NOT-FOUND";
        when(callService.findBySessionId(sessionId)).thenReturn(Optional.empty());

        ResponseEntity<Void> resp = controller.end(sessionId);

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        verify(callService, never()).end(any());
    }

    @Test
    void end_noDeberiaPasar_cuandoFindLanzaExcepcion() {
        String sessionId = "SESSION-EX";
        when(callService.findBySessionId(sessionId))
                .thenThrow(new RuntimeException("Repo error"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> controller.end(sessionId));

        assertEquals("Repo error", ex.getMessage());
        verify(callService, never()).end(any());
    }

    // -------------------------------------------------------------------------
    // iceServers()
    // -------------------------------------------------------------------------

    @Test
    void iceServers_deberiaRetornarStunYTurn_casoFeliz1() throws Exception {
        ReflectionTestUtils.setField(controller, "stunUrlsCsv", "stun:stun1,stun:stun2");
        ReflectionTestUtils.setField(controller, "turnUrlsCsv", "turn:turn1,turn:turn2");
        ReflectionTestUtils.setField(controller, "turnUser", "user1");
        ReflectionTestUtils.setField(controller, "turnPass", "secret");

        ResponseEntity<String> resp = controller.iceServers();

        assertEquals(HttpStatus.OK, resp.getStatusCode());

        String body = resp.getBody();
        assertNotNull(body);
        assertTrue(body.contains("stun:stun1"));
        assertTrue(body.contains("turn:turn1"));
        assertTrue(body.contains("username"));
        assertTrue(body.contains("credential"));
    }


    @Test
    void iceServers_deberiaRetornarSoloStun_cuandoTurnNoEstaConfigurado_casoFeliz2() throws Exception {
        ReflectionTestUtils.setField(controller, "stunUrlsCsv", "stun:only");
        ReflectionTestUtils.setField(controller, "turnUrlsCsv", "   ");
        ReflectionTestUtils.setField(controller, "turnUser", "   ");
        ReflectionTestUtils.setField(controller, "turnPass", "   ");

        ResponseEntity<String> resp = controller.iceServers();

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        String body = resp.getBody();
        assertNotNull(body);
        assertTrue(body.contains("stun:only"));
        assertFalse(body.contains("username"));
        assertFalse(body.contains("credential"));
    }

    @Test
    void iceServers_noDeberiaPasar_cuandoNoHayServidoresConfigurados() throws Exception {
        ReflectionTestUtils.setField(controller, "stunUrlsCsv", "   ");
        ReflectionTestUtils.setField(controller, "turnUrlsCsv", "   ");
        ReflectionTestUtils.setField(controller, "turnUser", "   ");
        ReflectionTestUtils.setField(controller, "turnPass", "   ");

        ResponseEntity<String> resp = controller.iceServers();

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("[]", resp.getBody());
    }

    @Test
    void iceServers_noDeberiaPasar_cuandoStunEsNull_lanzaNPE() {
        ReflectionTestUtils.setField(controller, "stunUrlsCsv", null);

        assertThrows(NullPointerException.class,
                () -> controller.iceServers());
    }

    // -------------------------------------------------------------------------
    // metrics()
    // -------------------------------------------------------------------------

    @Test
    void metrics_deberiaRetornarValoresDeSnapshot_casoFeliz1() {
        QualityMetricsService.Snapshot snap =
                new QualityMetricsService.Snapshot(100L, 200L, 0.9, 10);
        when(callService.snapshot()).thenReturn(snap);

        Map<String, Object> result = controller.metrics();

        assertEquals(100L, result.get("p95_ms"));
        assertEquals(200L, result.get("p99_ms"));
        assertEquals(0.9, (double) result.get("successRate5m"), 0.0001);
        assertEquals(10, result.get("samples"));
    }

    @Test
    void metrics_deberiaRetornarMapaCompleto_casoFeliz2() {
        QualityMetricsService.Snapshot snap =
                new QualityMetricsService.Snapshot(0L, 0L, 1.0, 0);
        when(callService.snapshot()).thenReturn(snap);

        Map<String, Object> result = controller.metrics();

        assertNotNull(result);
        assertTrue(result.containsKey("p95_ms"));
        assertTrue(result.containsKey("p99_ms"));
        assertTrue(result.containsKey("successRate5m"));
        assertTrue(result.containsKey("samples"));
    }

    @Test
    void metrics_noDeberiaPasar_cuandoSnapshotLanzaExcepcion() {
        when(callService.snapshot()).thenThrow(new RuntimeException("metrics error"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> controller.metrics());

        assertEquals("metrics error", ex.getMessage());
    }

    @Test
    void metrics_noDeberiaPasar_cuandoSnapshotEsNull() {
        when(callService.snapshot()).thenReturn(null);

        assertThrows(NullPointerException.class,
                () -> controller.metrics());
    }
}
