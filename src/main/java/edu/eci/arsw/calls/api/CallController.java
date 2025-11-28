package edu.eci.arsw.calls.api;

import edu.eci.arsw.calls.domain.CallSession;
import edu.eci.arsw.calls.service.QualityMetricsService;
import edu.eci.arsw.calls.service.CallSessionService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Controlador REST para la gestión de sesiones de llamadas.
 */
@RestController
@RequestMapping("/api/calls")
public class CallController {

    private final CallSessionService callService;
    private final ObjectMapper om = new ObjectMapper();

    @Value("${stun.urls}")
    private String stunUrlsCsv;
    @Value("${turn.urls:}")
    private String turnUrlsCsv;
    @Value("${turn.username:}")
    private String turnUser;
    @Value("${turn.password:}")
    private String turnPass;

    public CallController(CallSessionService callService) {
        this.callService = callService;
    }

    /**
     * Crea una nueva sesión de llamada basada en un ID de reserva.
     *
     * @param req  Mapa que contiene el ID de reserva.
     * @param auth Información de autenticación del usuario.
     * @return Mapa con detalles de la sesión creada.
     */
    @PostMapping("/session")
    public Map<String, Object> createSession(@RequestBody Map<String, String> req, Authentication auth) {
        String reservationId = req.get("reservationId");
        CallSession cs = callService.create(reservationId);
        return Map.of("sessionId", cs.getSessionId(), "reservationId", cs.getReservationId(), "ttlSeconds", 60 * 70);
    }

    /**
     * Marca una sesión de llamada como terminada.
     *
     * @param sessionId ID de la sesión a terminar.
     * @return Respuesta HTTP indicando el resultado de la operación.
     */
    @PostMapping("/{sessionId}/end")
    public ResponseEntity<Void> end(@PathVariable String sessionId) {
        return callService.findBySessionId(sessionId).map(cs -> {
            callService.end(cs);
            return ResponseEntity.ok().<Void>build();
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Proporciona una lista de servidores ICE (STUN/TURN) para la configuración de
     * WebRTC.
     *
     * @return Respuesta HTTP con la lista de servidores ICE en formato JSON.
     * @throws JsonProcessingException Si ocurre un error al procesar el JSON.
     */
    @GetMapping(value = "/ice-servers", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> iceServers() throws JsonProcessingException {
        List<Map<String, Object>> list = new ArrayList<>();

        // STUN
        for (String u : stunUrlsCsv.split("\\s*,\\s*")) {
            if (!u.isBlank())
                list.add(Map.of("urls", List.of(u)));
        }
        // TURN
        if (!turnUrlsCsv.isBlank() && !turnUser.isBlank() && !turnPass.isBlank()) {
            List<String> urls = Arrays.stream(turnUrlsCsv.split("\\s*,\\s*"))
                    .filter(s -> !s.isBlank()).toList();
            if (!urls.isEmpty()) {
                list.add(Map.of(
                        "urls", urls,
                        "username", turnUser,
                        "credential", turnPass));
            }
        }
        return ResponseEntity.ok(om.writeValueAsString(list));
    }

    /**
     * Proporciona métricas de calidad de las llamadas.
     *
     * @return Mapa con métricas como p95, p99, tasa de éxito y número de muestras.
     */
    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        QualityMetricsService.Snapshot s = callService.snapshot();
        return Map.of(
                "p95_ms", s.p95ms(),
                "p99_ms", s.p99ms(),
                "successRate5m", s.successRate(),
                "samples", s.samples());
    }
}