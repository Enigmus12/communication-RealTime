package edu.eci.arsw.calls.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.huxhorn.sulky.ulid.ULID;
import edu.eci.arsw.calls.service.CallSessionService;
import edu.eci.arsw.calls.service.EligibilityService;
import edu.eci.arsw.calls.pubsub.RedisPubSubBridge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CallWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(CallWebSocketHandler.class);

    private final ObjectMapper om = new ObjectMapper();
    private final ULID ulid = new ULID();

    private final SessionRegistry registry;
    private final CallSessionService callService;
    private final EligibilityService eligibilityService;
    private final RedisPubSubBridge bridge;
    private final Timer setupTimer;
    private final int heartbeatSeconds;
    private final int idleTimeoutSeconds;
    private final int rateLimit;

    private final Map<String, SimpleRateLimiter> limiters = new ConcurrentHashMap<>();

    /**
     * Constructor del manejador de WebSocket para llamadas
     */
    public CallWebSocketHandler(SessionRegistry registry,
            CallSessionService callService,
            EligibilityService eligibilityService,
            RedisPubSubBridge bridge,
            MeterRegistry meterRegistry,
            @Value("${WS_HEARTBEAT_SECONDS:10}") int heartbeatSeconds,
            @Value("${WS_IDLE_TIMEOUT_SECONDS:30}") int idleTimeoutSeconds,
            @Value("${WS_RATE_LIMIT:20}") int rateLimit) {
        this.registry = registry;
        this.callService = callService;
        this.eligibilityService = eligibilityService;
        this.bridge = bridge;
        this.heartbeatSeconds = heartbeatSeconds;
        this.idleTimeoutSeconds = idleTimeoutSeconds;
        this.rateLimit = rateLimit;
        this.setupTimer = meterRegistry.timer("call.setup.ms");
    }

    /**
     * Maneja mensajes de texto entrantes
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String userId = String.valueOf(session.getAttributes().get("userId"));
            limiters.computeIfAbsent(session.getId(), k -> new SimpleRateLimiter(rateLimit));
            if (!limiters.get(session.getId()).tryAcquire()) {
                session.close(CloseStatus.POLICY_VIOLATION);
                return;
            }

            MessageEnvelope env = om.readValue(message.getPayload(), MessageEnvelope.class);
            if (env.traceId == null || env.traceId.isBlank())
                env.traceId = ulid.nextULID();
            MDC.put("traceId", env.traceId);
            MDC.put("sessionId", env.sessionId);

            switch (env.type) {
                case "JOIN" -> onJoin(session, userId, env); // ⬅️ abajo
                case "OFFER", "ANSWER", "ICE_CANDIDATE" -> forward(env);
                case "HEARTBEAT" -> {
                    /* keepalive */ }
                case "LEAVE", "END" -> onEnd(env);
                default -> sendError(session, "Unsupported type");
            }

        } catch (Exception ex) {
            log.error("WS handleTextMessage failed", ex);
            // Envía error pero NO cierres a ciegas si es public/subscribe
            try {
                sendError(session, "500: " + ex.getClass().getSimpleName() + ": " +
                        (ex.getMessage() == null ? "no message" : ex.getMessage()));
            } catch (Exception ignore) {
            }
            // Cierra solo si el estado del WS ya no es válido:
            if (session.isOpen()) {
                try {
                    session.close(CloseStatus.SERVER_ERROR);
                } catch (Exception ignore) {
                }
            }
        } finally {
            MDC.clear();
        }
    }

    /**
     * Maneja la unión de un usuario a una sesión de llamada
     */
    private void onJoin(WebSocketSession session, String userId, MessageEnvelope env) throws IOException {
        if (userId == null || userId.isBlank()) {
            sendError(session, "Missing user identity");
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        if (env.sessionId == null || env.sessionId.isBlank()) {
            sendError(session, "Missing sessionId");
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }
        if (env.reservationId == null || env.reservationId.isBlank()) {
            sendError(session, "Missing reservationId");
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        String bearer = (String) session.getAttributes().get("token");
        var elig = eligibilityService.checkReservation(env.reservationId, userId, bearer);
        if (!elig.eligible()) {
            sendError(session, "403: " + elig.reason());
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        // Crea/recupera sesión
        var cs = callService.findBySessionId(env.sessionId).orElseGet(() -> callService.create(env.reservationId));
        registry.register(cs.getSessionId(), userId, session);

        // Guarda para limpieza al cerrar
        session.getAttributes().put("callSessionId", cs.getSessionId());
        session.getAttributes().put("callUserId", userId);

        // Suscripción pub/sub con fallback
        String channel = "call:" + cs.getSessionId();
        try {
            bridge.subscribe(channel, payload -> {
                try {
                    var sessions = registry.get(cs.getSessionId());
                    if (sessions == null || sessions.isEmpty())
                        return;
                    for (var entry : sessions.entrySet()) {
                        if (entry.getValue().isOpen()) {
                            entry.getValue().sendMessage(new TextMessage(payload));
                        }
                    }
                } catch (Exception e) {
                    log.warn("PubSub fanout failed", e);
                }
            });
        } catch (Exception e) {
            // No cierres. Queda solo fanout local ya suscrito.
            log.warn("No se pudo suscribir a Redis. Continuando con fallback local. {}", e.toString());
        }

        // ACK
        MessageEnvelope ack = new MessageEnvelope();
        ack.type = "JOIN_ACK";
        ack.sessionId = cs.getSessionId();
        ack.reservationId = env.reservationId;
        ack.from = "server";
        ack.to = userId;
        ack.ts = System.currentTimeMillis();
        ack.traceId = env.traceId;
        session.sendMessage(new TextMessage(om.writeValueAsString(ack)));

        // Broadcast JOINED
        MessageEnvelope joined = new MessageEnvelope();
        joined.type = "PEER_JOINED";
        joined.sessionId = cs.getSessionId();
        joined.reservationId = env.reservationId;
        joined.from = userId;
        joined.ts = System.currentTimeMillis();
        bridge.publish(channel, om.writeValueAsString(joined));
    }

    /**
     * Reenvía un mensaje a través del puente pub/sub
     */
    private void forward(MessageEnvelope env) throws IOException {
        bridge.publish("call:" + env.sessionId, om.writeValueAsString(env));
    }

    /**
     * Maneja la finalización de una sesión de llamada
     */
    private void onEnd(MessageEnvelope env) throws IOException {
        callService.findBySessionId(env.sessionId).ifPresent(callService::end);
        forward(env);
    }

    /**
     * Envía un mensaje de error al cliente
     */
    private void sendError(WebSocketSession session, String msg) throws IOException {
        MessageEnvelope err = new MessageEnvelope();
        err.type = "ERROR";
        err.payload = Map.of("message", msg);
        err.ts = System.currentTimeMillis();
        err.traceId = ulid.nextULID();
        session.sendMessage(new TextMessage(om.writeValueAsString(err)));
    }

    /**
     * Maneja el cierre de la conexión WebSocket
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        try {
            String sid = (String) session.getAttributes().get("callSessionId");
            String uid = (String) session.getAttributes().get("callUserId");
            if (sid != null && uid != null) {
                registry.unregister(sid, uid);
                // Notifica salida
                MessageEnvelope left = new MessageEnvelope();
                left.type = "PEER_LEFT";
                left.sessionId = sid;
                left.from = uid;
                left.ts = System.currentTimeMillis();
                bridge.publish("call:" + sid, new ObjectMapper().writeValueAsString(left));
            }
        } catch (Exception ignore) {
        }
    }
}
