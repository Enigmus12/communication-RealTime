package edu.eci.arsw.calls.api;

import edu.eci.arsw.calls.domain.CallSession;
import edu.eci.arsw.calls.service.CallSessionService;
import edu.eci.arsw.calls.service.QualityMetricsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/calls")
public class CallController {

    private final CallSessionService callService;

    @Value("${ICE_SERVERS_JSON:[{\"urls\":[\"stun:stun1.l.google.com:19302\"]}]}") 
    private String iceServersJson;

    public CallController(CallSessionService callService) {
        this.callService = callService;
    }

    @PostMapping("/session")
    public Map<String,Object> createSession(@RequestBody Map<String,String> req, Authentication auth) {
        String reservationId = req.get("reservationId");
        CallSession cs = callService.create(reservationId);
        return Map.of("sessionId", cs.getSessionId(), "reservationId", cs.getReservationId(), "ttlSeconds", 60*70);
    }

    @PostMapping("/{sessionId}/end")
    public ResponseEntity<?> end(@PathVariable String sessionId) {
        return callService.findBySessionId(sessionId).map(cs -> {
            callService.end(cs);
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(value="/ice-servers", produces=MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> iceServers() {
        return ResponseEntity.ok(iceServersJson);
    }

    @GetMapping("/metrics")
    public Map<String,Object> metrics() {
        QualityMetricsService.Snapshot s = callService.snapshot();
        return Map.of(
                "p95_ms", s.p95ms(),
                "p99_ms", s.p99ms(),
                "successRate5m", s.successRate(),
                "samples", s.samples()
        );
    }
}
