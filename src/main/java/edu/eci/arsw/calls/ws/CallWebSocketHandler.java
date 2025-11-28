package edu.eci.arsw.calls.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.huxhorn.sulky.ulid.ULID;
import edu.eci.arsw.calls.domain.CallSession;
import edu.eci.arsw.calls.pubsub.RedisPubSubBridge;
import edu.eci.arsw.calls.service.EligibilityService;
import edu.eci.arsw.calls.service.CallSessionService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manejador WebSocket para la comunicación en tiempo real de llamadas.
 */
@Component
public class CallWebSocketHandler extends TextWebSocketHandler {
    private static final Logger log = LoggerFactory.getLogger(CallWebSocketHandler.class);

    private static final String CALL_CHANNEL_PREFIX = "call:";

    private final ObjectMapper om = new ObjectMapper();
    private final ULID ulid = new ULID();

    private final SessionRegistry registry;
    private final CallSessionService callService;
    private final EligibilityService eligibilityService;
    private final RedisPubSubBridge bridge;

    private final int rateLimit;

    /** Límite de tasa por sesión */
    private final Map<String, SimpleRateLimiter> limiters = new ConcurrentHashMap<>();
    /** Evita suscribirse más de una vez al mismo canal */
    private final Set<String> subscribedChannels = ConcurrentHashMap.newKeySet();

    public CallWebSocketHandler(SessionRegistry registry,
                                CallSessionService callService,
                                EligibilityService eligibilityService,
                                RedisPubSubBridge bridge,
                                @Value("${WS_RATE_LIMIT:20}") int rateLimit) {
        this.registry = registry;
        this.callService = callService;
        this.eligibilityService = eligibilityService;
        this.bridge = bridge;
        this.rateLimit = rateLimit;
    }

    /**
     * Límite de tasa por sesión
     *
     * @param session Sesión WebSocket
     * @param message Mensaje de texto recibido
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            limiters.computeIfAbsent(session.getId(), k -> new SimpleRateLimiter(rateLimit));
            if (!limiters.get(session.getId()).tryAcquire()) {
                session.close(CloseStatus.POLICY_VIOLATION);
                return;
            }

            handleInboundMessage(session, message);

        } catch (Exception ex) {
            log.error("WS handleTextMessage failed", ex);
            try {
                sendError(session, "500: " + ex.getClass().getSimpleName() + ": "
                        + (ex.getMessage() == null ? "no message" : ex.getMessage()));
            } catch (Exception ignore) {
                /* noop */ }
            if (session.isOpen()) {
                try {
                    session.close(CloseStatus.SERVER_ERROR);
                } catch (Exception ignore) {
                    /* noop */ }
            }
        } finally {
            MDC.clear();
        }
    }

    /**
     * Maneja el mensaje entrante basado en su tipo.
     *
     * @param session Sesión WebSocket
     * @param message Mensaje de texto recibido
     * @throws IOException Si ocurre un error de E/S
     */
    private void handleInboundMessage(WebSocketSession session, TextMessage message) throws IOException {
        String userId = String.valueOf(session.getAttributes().get("userId"));

        MessageEnvelope env = om.readValue(message.getPayload(), MessageEnvelope.class);
        if (env.traceId == null || env.traceId.isBlank()) {
            env.traceId = ulid.nextULID();
        }
        MDC.put("traceId", env.traceId);
        MDC.put("sessionId", env.sessionId);

        switch (env.type) {
            case "JOIN" -> onJoin(session, userId, env);
            case "OFFER", "ANSWER", "ICE_CANDIDATE" -> forwardAndInspect(env);
            case "RTC_CONNECTED" -> onRtcConnected(env);
            case "HEARTBEAT" -> {
                /* keepalive */ }
            case "LEAVE", "END" -> onEnd(env);
            default -> sendError(session, "Unsupported type");
        }
    }

    /**
     * Maneja la lógica de unión a una sesión de llamada.
     *
     * @param session Sesión WebSocket del usuario.
     * @param userId  ID del usuario que se une.
     * @param env     Mensaje de unión recibido.
     * @throws IOException Si ocurre un error de E/S.
     */
    private void onJoin(WebSocketSession session, String userId, MessageEnvelope env) throws IOException {
        if (!validateUserAndSession(session, userId, env)) {
            return;
        }

        final String reservationId = resolveReservationId(env);
        if (!validateReservation(session, reservationId)) {
            return;
        }

        if (!checkEligibility(session, userId, reservationId)) {
            return;
        }

        CallSession cs = getOrCreateCallSession(env, reservationId);
        int currentParticipants = countParticipants(cs);
        if (!ensureCapacity(session, currentParticipants)) {
            return;
        }

        boolean initiator = (currentParticipants == 0);

        registerParticipant(session, userId, cs);
        subscribeChannelIfNeeded(cs);
        sendJoinAck(session, userId, env, cs, initiator);
        notifyPeerJoined(cs, userId);
    }

    /**
     * Valida la identidad del usuario y el ID de sesión.
     *
     * @param session Sesión WebSocket.
     * @param userId  ID del usuario.
     * @param env     Mensaje recibido.
     * @return true si la validación es exitosa, false en caso contrario.
     * @throws IOException Si ocurre un error de E/S.
     */
    private boolean validateUserAndSession(WebSocketSession session,
                                           String userId,
                                           MessageEnvelope env) throws IOException {
        if (userId == null || userId.isBlank()) {
            sendError(session, "Missing user identity");
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return false;
        }
        if (env.sessionId == null || env.sessionId.isBlank()) {
            sendError(session, "Missing sessionId");
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return false;
        }
        return true;
    }

    /**
     * Resuelve el ID de reserva a partir del mensaje recibido.
     *
     * @param env Mensaje recibido.
     * @return ID de la reserva resuelta.
     */
    private String resolveReservationId(MessageEnvelope env) {
        if (env.reservationId != null && !env.reservationId.isBlank()) {
            return env.reservationId;
        }
        return callService.findBySessionId(env.sessionId)
                .map(CallSession::getReservationId)
                .orElse(null);
    }

    /**
     * Valida el ID de reserva.
     *
     * @param session              Sesión WebSocket.
     * @param resolvedReservationId ID de la reserva resuelta.
     * @return true si la validación es exitosa, false en caso contrario.
     * @throws IOException Si ocurre un error de E/S.
     */
    private boolean validateReservation(WebSocketSession session,
                                        String resolvedReservationId) throws IOException {
        if (resolvedReservationId == null || resolvedReservationId.isBlank()) {
            sendError(session, "Missing reservationId and unknown sessionId");
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return false;
        }
        return true;
    }

    /**
     * Verifica la elegibilidad del usuario para la reserva.
     *
     * @param session       Sesión WebSocket.
     * @param userId        ID del usuario.
     * @param reservationId ID de la reserva.
     * @return true si el usuario es elegible, false en caso contrario.
     * @throws IOException Si ocurre un error de E/S.
     */
    private boolean checkEligibility(WebSocketSession session,
                                     String userId,
                                     String reservationId) throws IOException {
        String bearer = (String) session.getAttributes().get("token");
        var elig = eligibilityService.checkReservation(reservationId, userId, bearer);
        if (!elig.eligible()) {
            sendError(session, "403: " + elig.reason());
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return false;
        }
        return true;
    }

    /**
     * Obtiene o crea una sesión de llamada basada en el ID de reserva.
     *
     * @param env                    Mensaje recibido.
     * @param resolvedReservationId ID de la reserva resuelta.
     * @return Sesión de llamada existente o nueva.
     */
    private CallSession getOrCreateCallSession(MessageEnvelope env,
                                               String resolvedReservationId) {
        return callService.findBySessionId(env.sessionId)
                .map(existing -> {
                    if (!Objects.equals(existing.getReservationId(), resolvedReservationId)) {
                        throw new IllegalStateException("Session mismatches reservation");
                    }
                    return existing;
                })
                .orElseGet(() -> callService.create(resolvedReservationId));
    }

    /**
     * Cuenta el número de participantes en una sesión de llamada.
     *
     * @param cs Sesión de llamada.
     * @return Número de participantes actuales.
     */
    private int countParticipants(CallSession cs) {
        var participants = registry.get(cs.getSessionId());
        return (participants == null) ? 0 : participants.size();
    }

    /**
     * Asegura que la sesión no exceda la capacidad máxima de participantes.
     *
     * @param session       Sesión WebSocket.
     * @param currentCount Número actual de participantes.
     * @return true si hay capacidad, false en caso contrario.
     * @throws IOException Si ocurre un error de E/S.
     */
    private boolean ensureCapacity(WebSocketSession session,
                                   int currentCount) throws IOException {
        if (currentCount >= 2) {
            sendError(session, "Room full");
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return false;
        }
        return true;
    }

    /**
     * Registra un participante en la sesión de llamada.
     *
     * @param session Sesión WebSocket.
     * @param userId  ID del usuario.
     * @param cs      Sesión de llamada.
     */
    private void registerParticipant(WebSocketSession session,
                                     String userId,
                                     CallSession cs) {
        registry.register(cs.getSessionId(), userId, session);
        session.getAttributes().put("callSessionId", cs.getSessionId());
        session.getAttributes().put("callUserId", userId);
        session.getAttributes().put("callReservationId", cs.getReservationId());
    }

    /**
     * Se suscribe al canal de la sesión de llamada si no está ya suscrito.
     *
     * @param cs Sesión de llamada.
     */
    private void subscribeChannelIfNeeded(CallSession cs) {
        String channel = CALL_CHANNEL_PREFIX + cs.getSessionId();
        if (!subscribedChannels.add(channel)) {
            return;
        }
        try {
            bridge.subscribe(channel, payload -> {
                try {
                    var msg = om.readValue(payload, MessageEnvelope.class);
                    var sessMap = registry.get(cs.getSessionId());
                    if (sessMap == null || sessMap.isEmpty()) {
                        return;
                    }

                    for (var entry : sessMap.entrySet()) {
                        String targetUserId = entry.getKey();
                        var ws = entry.getValue();
                        if (!Objects.equals(targetUserId, msg.from) && ws.isOpen()) {
                            ws.sendMessage(new TextMessage(payload));
                        }
                    }
                } catch (Exception e) {
                    log.warn("PubSub fanout failed", e);
                }
            });
        } catch (Exception e) {
            log.warn("No se pudo suscribir a Redis. Fallback local. {}", e.toString());
        }
    }

    /**
     * Envía un acuse de recibo de unión al usuario.
     *
     * @param session   Sesión WebSocket.
     * @param userId    ID del usuario.
     * @param env       Mensaje recibido.
     * @param cs        Sesión de llamada.
     * @param initiator Indica si el usuario es el iniciador de la llamada.
     * @throws IOException Si ocurre un error de E/S.
     */
    private void sendJoinAck(WebSocketSession session,
                             String userId,
                             MessageEnvelope env,
                             CallSession cs,
                             boolean initiator) throws IOException {
        MessageEnvelope ack = new MessageEnvelope();
        ack.type = "JOIN_ACK";
        ack.sessionId = cs.getSessionId();
        ack.reservationId = cs.getReservationId();
        ack.from = "server";
        ack.to = userId;
        ack.ts = System.currentTimeMillis();
        ack.traceId = env.traceId;
        ack.payload = Map.of("initiator", initiator);
        session.sendMessage(new TextMessage(om.writeValueAsString(ack)));
    }

    /**
     * Notifica a los demás participantes que un nuevo par se ha unido.
     *
     * @param cs     Sesión de llamada.
     * @param userId ID del usuario que se unió.
     * @throws IOException Si ocurre un error de E/S.
     */
    private void notifyPeerJoined(CallSession cs, String userId) throws IOException {
        MessageEnvelope joined = new MessageEnvelope();
        joined.type = "PEER_JOINED";
        joined.sessionId = cs.getSessionId();
        joined.reservationId = cs.getReservationId();
        joined.from = userId;
        joined.ts = System.currentTimeMillis();
        bridge.publish(CALL_CHANNEL_PREFIX + cs.getSessionId(), om.writeValueAsString(joined));
    }

    /**
     * Reenvía el mensaje y realiza inspecciones adicionales.
     *
     * @param env El mensaje a reenviar e inspeccionar.
     * @throws IOException Si ocurre un error de E/S.
     */
    private void forwardAndInspect(MessageEnvelope env) throws IOException {
        String payloadStr = om.writeValueAsString(env);
        bridge.publish(CALL_CHANNEL_PREFIX + env.sessionId, payloadStr);

        // Detecta uso de TURN
        if ("ICE_CANDIDATE".equals(env.type) && env.payload instanceof Map<?, ?> map) {
            Object cand = map.get("candidate");
            if (cand != null && String.valueOf(cand).contains(" typ relay")) {
                callService.findBySessionId(env.sessionId).ifPresent(cs -> cs.setTurnUsed(true));
            }
        }
    }

    /**
     * Maneja la lógica cuando se establece la conexión RTC.
     *
     * @param env El mensaje de conexión RTC.
     * @throws IOException Si ocurre un error de E/S.
     */
    private void onRtcConnected(MessageEnvelope env) throws IOException {
        callService.findBySessionId(env.sessionId).ifPresent(callService::markConnected);
        bridge.publish(CALL_CHANNEL_PREFIX + env.sessionId, om.writeValueAsString(env));
    }

    /**
     * Maneja la lógica de finalización de una sesión de llamada.
     *
     * @param env El mensaje de finalización recibido.
     * @throws IOException Si ocurre un error de E/S.
     */
    private void onEnd(MessageEnvelope env) throws IOException {
        callService.findBySessionId(env.sessionId).ifPresent(callService::end);
        bridge.publish(CALL_CHANNEL_PREFIX + env.sessionId, om.writeValueAsString(env));
    }

    /**
     * Envía un mensaje de error a la sesión WebSocket.
     *
     * @param session La sesión WebSocket a la que enviar el error.
     * @param msg     El mensaje de error.
     * @throws IOException Si ocurre un error de E/S.
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
     * Maneja la lógica cuando se cierra una conexión WebSocket.
     *
     * @param session La sesión WebSocket que se cerró.
     * @param status  El estado de cierre.
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        try {
            String sid = (String) session.getAttributes().get("callSessionId");
            String uid = (String) session.getAttributes().get("callUserId");
            if (sid != null && uid != null) {
                registry.unregister(sid, uid);
                MessageEnvelope left = new MessageEnvelope();
                left.type = "PEER_LEFT";
                left.sessionId = sid;
                left.from = uid;
                left.ts = System.currentTimeMillis();
                bridge.publish(
                        CALL_CHANNEL_PREFIX + sid,
                        new ObjectMapper().writeValueAsString(left)
                );
            }
        } catch (Exception ignore) {
            /* noop */ }
    }
}
