package edu.eci.arsw.calls.api;

import edu.eci.arsw.calls.domain.CallSession;
import edu.eci.arsw.calls.service.CallSessionService;
import edu.eci.arsw.calls.service.QualityMetricsService;
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

    @PostMapping("/session")
    public Map<String, Object> createSession(@RequestBody Map<String, String> req, Authentication auth) {
        String reservationId = req.get("reservationId");
        CallSession cs = callService.create(reservationId);
        return Map.of("sessionId", cs.getSessionId(), "reservationId", cs.getReservationId(), "ttlSeconds", 60 * 70);
    }

    @PostMapping("/{sessionId}/end")
    public ResponseEntity<?> end(@PathVariable String sessionId) {
        return callService.findBySessionId(sessionId).map(cs -> {
            callService.end(cs);
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/ice-servers", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> iceServers() throws JsonProcessingException {
        List<Map<String, Object>> list = new ArrayList<>();

        // STUN
        for (String u : stunUrlsCsv.split("\\s*,\\s*")) {
            if (!u.isBlank())
                list.add(Map.of("urls", List.of(u)));
        }
        // TURN (si está configurado)
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
