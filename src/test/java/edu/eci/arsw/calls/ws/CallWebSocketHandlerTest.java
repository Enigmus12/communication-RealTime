package edu.eci.arsw.calls.ws;

import edu.eci.arsw.calls.domain.CallSession;
import edu.eci.arsw.calls.pubsub.RedisPubSubBridge;
import edu.eci.arsw.calls.service.CallSessionService;
import edu.eci.arsw.calls.service.EligibilityService;
import edu.eci.arsw.calls.service.EligibilityService.EligibilityResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias para CallWebSocketHandler.
 * Se enfoca en los caminos principales de handleTextMessage y afterConnectionClosed.
 */
class CallWebSocketHandlerTest {

    private static final String TEST_BEARER = "Bearer XYZ";
    private static final String TEST_RESERVATION_ID = "RES-1";

    private SessionRegistry registry;
    private CallSessionService callService;
    private EligibilityService eligibilityService;
    private RedisPubSubBridge bridge;

    private CallWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        registry = new SessionRegistry();
        callService = mock(CallSessionService.class);
        eligibilityService = mock(EligibilityService.class);
        bridge = mock(RedisPubSubBridge.class);

        handler = new CallWebSocketHandler(registry, callService, eligibilityService, bridge, 10);
    }

    private WebSocketSession buildSession(String id, String userId, String token) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attrs = new ConcurrentHashMap<>();
        if (userId != null) {
            attrs.put("userId", userId);
        }
        if (token != null) {
            attrs.put("token", token);
        }
        when(session.getId()).thenReturn(id);
        when(session.getAttributes()).thenReturn(attrs);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    // ---------------------------------------------------------------------
    // handleTextMessage - JOIN happy path
    // ---------------------------------------------------------------------

    @Test
    void handleTextMessageShouldProcessJoinHappyPath() throws Exception {
        WebSocketSession session = buildSession("WS1", "U1", TEST_BEARER);

        when(eligibilityService.checkReservation(eq(TEST_RESERVATION_ID), eq("U1"), anyString()))
                .thenReturn(EligibilityResult.ok());

        when(callService.findBySessionId("SID-1")).thenReturn(Optional.empty());
        CallSession cs = CallSession.create("CS-1", TEST_RESERVATION_ID, Instant.now());
        when(callService.create(TEST_RESERVATION_ID)).thenReturn(cs);

        String json = """
                {
                  "type": "JOIN",
                  "sessionId": "SID-1",
                  "reservationId": "RES-1",
                  "from": "U1"
                }
                """;

        handler.handleTextMessage(session, new TextMessage(json));

        Map<String, WebSocketSession> participantes = registry.get("CS-1");
        assertNotNull(participantes);
        assertEquals(1, participantes.size());
        assertTrue(participantes.containsKey("U1"));

        verify(session).sendMessage(argThat(message -> {
            if (!(message instanceof TextMessage)) {
                return false;
            }
            String payload = ((TextMessage) message).getPayload();
            return payload.contains("\"JOIN_ACK\"")
                    && payload.contains("\"initiator\":true");
        }));

        verify(bridge).publish(startsWith("call:CS-1"), anyString());
    }

    // ---------------------------------------------------------------------
    // Validaciones negativas en JOIN
    // ---------------------------------------------------------------------

    @Test
    void handleTextMessageShouldRejectJoinWhenUserIdMissing() throws Exception {
        WebSocketSession session = buildSession("WS1", "   ", TEST_BEARER);

        String json = """
                {"type":"JOIN","sessionId":"SID-1","reservationId":"RES-1","from":"X"}
                """;

        handler.handleTextMessage(session, new TextMessage(json));

        verify(session).sendMessage(argThat(message -> {
            if (!(message instanceof TextMessage)) {
                return false;
            }
            String payload = ((TextMessage) message).getPayload();
            return payload.contains("\"ERROR\"")
                    && payload.contains("Missing user identity");
        }));
        verify(session).close(CloseStatus.NOT_ACCEPTABLE);

        verifyNoInteractions(callService, eligibilityService, bridge);
    }

    @Test
    void handleTextMessageShouldRejectJoinWhenReservationMissingAndUnknownSession() throws Exception {
        WebSocketSession session = buildSession("WS1", "U1", TEST_BEARER);

        // sessionId existe pero scheduler no conoce esa sesión
        when(callService.findBySessionId("SID-1")).thenReturn(Optional.empty());

        String json = """
                {"type":"JOIN","sessionId":"SID-1"}
                """;

        handler.handleTextMessage(session, new TextMessage(json));

        verify(session).sendMessage(argThat(message -> {
            if (!(message instanceof TextMessage)) {
                return false;
            }
            String payload = ((TextMessage) message).getPayload();
            return payload.contains("\"ERROR\"")
                    && payload.contains("Missing reservationId and unknown sessionId");
        }));
        verify(session).close(CloseStatus.NOT_ACCEPTABLE);

        verify(callService).findBySessionId("SID-1");
        verify(callService, never()).create(anyString());
        verifyNoInteractions(eligibilityService, bridge);
    }

    @Test
    void handleTextMessageShouldRejectJoinWhenEligibilityFails() throws Exception {
        WebSocketSession session = buildSession("WS1", "U1", TEST_BEARER);

        when(eligibilityService.checkReservation(eq(TEST_RESERVATION_ID), eq("U1"), anyString()))
                .thenReturn(EligibilityResult.notEligible("nope"));

        String json = """
                {"type":"JOIN","sessionId":"SID-1","reservationId":"RES-1","from":"U1"}
                """;

        handler.handleTextMessage(session, new TextMessage(json));

        verify(session).sendMessage(argThat(message -> {
            if (!(message instanceof TextMessage)) {
                return false;
            }
            String payload = ((TextMessage) message).getPayload();
            return payload.contains("\"ERROR\"")
                    && payload.contains("403: nope");
        }));
        verify(session).close(CloseStatus.NOT_ACCEPTABLE);

        verify(eligibilityService).checkReservation(eq(TEST_RESERVATION_ID), eq("U1"), anyString());
        verify(callService, never()).create(anyString());
        verifyNoInteractions(bridge);
        assertTrue(registry.all().isEmpty());
    }

    @Test
    void handleTextMessageShouldRejectJoinWhenRoomIsFull() throws Exception {
        WebSocketSession session = buildSession("WS3", "U3", TEST_BEARER);

        when(eligibilityService.checkReservation(eq(TEST_RESERVATION_ID), eq("U3"), anyString()))
                .thenReturn(EligibilityResult.ok());

        CallSession cs = CallSession.create("CS-ROOM", TEST_RESERVATION_ID, Instant.now());
        when(callService.findBySessionId("SID-ROOM")).thenReturn(Optional.of(cs));

        registry.register("CS-ROOM", "U1", mock(WebSocketSession.class));
        registry.register("CS-ROOM", "U2", mock(WebSocketSession.class));

        String json = """
                {"type":"JOIN","sessionId":"SID-ROOM","reservationId":"RES-1","from":"U3"}
                """;

        handler.handleTextMessage(session, new TextMessage(json));

        verify(session).sendMessage(argThat(message -> {
            if (!(message instanceof TextMessage)) {
                return false;
            }
            String payload = ((TextMessage) message).getPayload();
            return payload.contains("\"ERROR\"")
                    && payload.contains("Room full");
        }));
        verify(session).close(CloseStatus.NOT_ACCEPTABLE);

        Map<String, WebSocketSession> participants = registry.get("CS-ROOM");
        assertEquals(2, participants.size()); // sigue sin entrar el tercero
    }

    @Test
    void handleTextMessageShouldReturn500WhenSessionReservationMismatch() throws Exception {
        WebSocketSession session = buildSession("WS1", "U1", TEST_BEARER);

        when(eligibilityService.checkReservation(eq(TEST_RESERVATION_ID), eq("U1"), anyString()))
                .thenReturn(EligibilityResult.ok());

        CallSession cs = CallSession.create("CS-1", "OTHER-RES", Instant.now());
        when(callService.findBySessionId("SID-1")).thenReturn(Optional.of(cs));

        String json = """
                {"type":"JOIN","sessionId":"SID-1","reservationId":"RES-1","from":"U1"}
                """;

        handler.handleTextMessage(session, new TextMessage(json));

        verify(session).sendMessage(argThat(message -> {
            if (!(message instanceof TextMessage)) {
                return false;
            }
            String payload = ((TextMessage) message).getPayload();
            return payload.contains("\"ERROR\"")
                    && payload.contains("IllegalStateException");
        }));
        try {
            verify(session).close(CloseStatus.SERVER_ERROR);
        } catch (IOException e) {
            fail("IOException thrown during close verification: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------------
    // Rate limiting y errores genéricos
    // ---------------------------------------------------------------------

    @Test
    void handleTextMessageShouldCloseWhenRateLimitExceeded() throws Exception {
        handler = new CallWebSocketHandler(registry, callService, eligibilityService, bridge, 1);

        WebSocketSession session = buildSession("WS1", "U1", TEST_BEARER);

        String heartbeatJson = """
                {"type":"HEARTBEAT","sessionId":"SID-1"}
                """;

        handler.handleTextMessage(session, new TextMessage(heartbeatJson));

        handler.handleTextMessage(session, new TextMessage(heartbeatJson));

        verify(session).close(CloseStatus.POLICY_VIOLATION);
        verifyNoInteractions(callService, eligibilityService);
    }

    @Test
    void handleTextMessageShouldSendErrorWhenJsonIsInvalid() {
        WebSocketSession session = buildSession("WS1", "U1", TEST_BEARER);

        handler.handleTextMessage(session, new TextMessage("esto-no-es-json"));

        try {
            verify(session).sendMessage(argThat(message -> {
                if (!(message instanceof TextMessage)) return false;
                String payload = ((TextMessage) message).getPayload();
                return payload.contains("\"ERROR\"");
            }));
        } catch (IOException e) {
            fail("IOException thrown during sendMessage verification: " + e.getMessage());
        }
        try {
            verify(session).close(CloseStatus.SERVER_ERROR);
        } catch (IOException e) {
            fail("IOException thrown during close verification: " + e.getMessage());
        }
    }

    @Test
    void handleTextMessageOnErrorShouldNotCloseWhenSessionAlreadyClosed() {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attrs = new ConcurrentHashMap<>();
        attrs.put("userId", "U1");
        when(session.getAttributes()).thenReturn(attrs);
        when(session.isOpen()).thenReturn(false);

        handler.handleTextMessage(session, new TextMessage("no-json"));

        try {
            verify(session).sendMessage(argThat(message -> {
                if (!(message instanceof TextMessage)) return false;
                String payload = ((TextMessage) message).getPayload();
                return payload.contains("\"ERROR\"");
            }));
        } catch (IOException e) {
            fail("IOException thrown during sendMessage verification: " + e.getMessage());
        }
        try {
            verify(session, never()).close(any());
        } catch (IOException e) {
            fail("IOException thrown during close verification: " + e.getMessage());
        }
    }

    @Test
    void handleTextMessageShouldSendErrorWhenTypeUnsupported() throws Exception {
        WebSocketSession session = buildSession("WS1", "U1", TEST_BEARER);

        String json = """
                {"type":"UNKNOWN","sessionId":"SID-1","reservationId":"RES-1"}
                """;

        handler.handleTextMessage(session, new TextMessage(json));

        verify(session).sendMessage(argThat(message -> {
            if (!(message instanceof TextMessage)) {
                return false;
            }
            String payload = ((TextMessage) message).getPayload();
            return payload.contains("\"ERROR\"")
                    && payload.contains("Unsupported type");
        }));
        verify(session, never()).close(any());
    }

    // ---------------------------------------------------------------------
    // Tipos OFFER / ICE_CANDIDATE / RTC_CONNECTED / END
    // ---------------------------------------------------------------------

    @Test
    void handleTextMessageShouldForwardOfferThroughBridge() {
        WebSocketSession session = buildSession("WS1", "U1", TEST_BEARER);

        String json = """
                {"type":"OFFER","sessionId":"SID-1","payload":{"sdp":"x"}}
                """;

        handler.handleTextMessage(session, new TextMessage(json));

        verify(bridge).publish(eq("call:SID-1"), anyString());
        verifyNoInteractions(callService);
    }

    @Test
    void handleTextMessageShouldMarkTurnUsedOnRelayCandidate() {
        WebSocketSession session = buildSession("WS1", "U1", TEST_BEARER);

        CallSession cs = CallSession.create("CS-1", TEST_RESERVATION_ID, Instant.now());
        when(callService.findBySessionId("SID-1")).thenReturn(Optional.of(cs));

        String json = """
                {
                  "type":"ICE_CANDIDATE",
                  "sessionId":"SID-1",
                  "payload":{
                    "candidate":"candidate:1 1 udp 2122260223 192.0.2.1 54400 typ relay raddr 0.0.0.0 rport 0"
                  }
                }
                """;

        handler.handleTextMessage(session, new TextMessage(json));

        verify(bridge).publish(eq("call:SID-1"), anyString());
        verify(callService).findBySessionId("SID-1"); 
    }

    @Test
    void handleTextMessageShouldMarkConnectedOnRtcConnected() {
        WebSocketSession session = buildSession("WS1", "U1", TEST_BEARER);

        CallSession cs = CallSession.create("CS-1", TEST_RESERVATION_ID, Instant.now());
        when(callService.findBySessionId("SID-1")).thenReturn(Optional.of(cs));

        String json = """
                {"type":"RTC_CONNECTED","sessionId":"SID-1"}
                """;

        handler.handleTextMessage(session, new TextMessage(json));

        verify(callService).markConnected(cs);
        verify(bridge).publish(eq("call:SID-1"), anyString());
    }

    @Test
    void handleTextMessageShouldEndSessionOnEndMessage() {
        WebSocketSession session = buildSession("WS1", "U1", TEST_BEARER);

        CallSession cs = CallSession.create("CS-1", TEST_RESERVATION_ID, Instant.now());
        when(callService.findBySessionId("SID-1")).thenReturn(Optional.of(cs));

        String json = """
                {"type":"END","sessionId":"SID-1"}
                """;

        handler.handleTextMessage(session, new TextMessage(json));

        verify(callService).end(cs);
        verify(bridge).publish(eq("call:SID-1"), anyString());
    }

    // ---------------------------------------------------------------------
    // Fan-out real usando RedisPubSubBridge en modo local
    // ---------------------------------------------------------------------

    @Test
    void joinShouldFanoutPeerJoinedToOtherParticipants() throws Exception {
        RedisPubSubBridge realBridge = new RedisPubSubBridge(null, null);
        handler = new CallWebSocketHandler(registry, callService, eligibilityService, realBridge, 10);

        when(eligibilityService.checkReservation(eq(TEST_RESERVATION_ID), anyString(), anyString()))
                .thenReturn(EligibilityResult.ok());

        CallSession cs = CallSession.create("CS-1", TEST_RESERVATION_ID, Instant.now());
        when(callService.findBySessionId("SID-1")).thenReturn(Optional.empty());
        when(callService.create(TEST_RESERVATION_ID)).thenReturn(cs);

        WebSocketSession ws1 = buildSession("WS1", "U1", TEST_BEARER);
        String join1 = """
                {"type":"JOIN","sessionId":"SID-1","reservationId":"RES-1","from":"U1"}
                """;
        handler.handleTextMessage(ws1, new TextMessage(join1));

        WebSocketSession ws2 = buildSession("WS2", "U2", TEST_BEARER);
        String join2 = """
                {"type":"JOIN","sessionId":"SID-1","reservationId":"RES-1","from":"U2"}
                """;
        handler.handleTextMessage(ws2, new TextMessage(join2));

        verify(ws1, atLeastOnce()).sendMessage(argThat(message -> {
            if (!(message instanceof TextMessage)) return false;
            String payload = ((TextMessage) message).getPayload();
            return payload.contains("\"PEER_JOINED\"")
                    && payload.contains("\"from\":\"U2\"");
        }));
    }

    // ---------------------------------------------------------------------
    // afterConnectionClosed
    // ---------------------------------------------------------------------

    @Test
    void afterConnectionClosedShouldUnregisterAndPublishPeerLeft() {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attrs = new ConcurrentHashMap<>();
        attrs.put("callSessionId", "CS-1");
        attrs.put("callUserId", "U1");
        when(session.getAttributes()).thenReturn(attrs);

        registry.register("CS-1", "U1", mock(WebSocketSession.class));

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        assertTrue(registry.get("CS-1").isEmpty());

        verify(bridge).publish(eq("call:CS-1"), argThat(payload ->
                payload.contains("\"PEER_LEFT\"") && payload.contains("\"U1\"")));
    }

    @Test
    void afterConnectionClosedShouldDoNothingWhenMissingAttributes() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(new ConcurrentHashMap<>());

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        assertTrue(registry.all().isEmpty());
        verifyNoInteractions(bridge);
    }
}
