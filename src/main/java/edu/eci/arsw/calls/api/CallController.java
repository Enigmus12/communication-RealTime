package edu.eci.arsw.calls.api;

import edu.eci.arsw.calls.service.CallSessionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
/**
 * Controlador REST para gestionar las llamadas
 */
@RestController
@RequestMapping("/api/calls")
public class CallController {
    private final CallSessionService callService;

    @Value("${ICE_SERVERS_JSON:[{\"urls\":[\"stun:stun1.l.google.com:19302\"]}]}")
    private String iceServersJson;

    public CallController(CallSessionService callService) {
        this.callService = callService;
    }

    /**
     * Crear una nueva sesión de llamada
     */
    @PostMapping("/session")
    public Map<String,Object> createSession(@RequestBody Map<String,String> req, Authentication auth) {
        String reservationId = req.get("reservationId");
        var cs = callService.create(reservationId);
        return Map.of("sessionId", cs.getSessionId(), "reservationId", cs.getReservationId(), "ttlSeconds", 60*70);
    }

    /**
     * Finalizar una sesión de llamada
     */
    @PostMapping("/{sessionId}/end")
    public ResponseEntity<?> end(@PathVariable String sessionId) {
        return callService.findBySessionId(sessionId).map(cs -> {
            callService.end(cs);
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }
    /**
     * Obtener configuración de servidores ICE
     */
    @GetMapping("/ice-servers")
    public ResponseEntity<String> iceServers() {
        return ResponseEntity.ok(iceServersJson);
    }
}
