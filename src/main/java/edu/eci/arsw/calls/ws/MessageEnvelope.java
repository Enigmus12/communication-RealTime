package edu.eci.arsw.calls.ws;

import com.fasterxml.jackson.annotation.JsonAutoDetect;

/**
 * Sobre de mensaje para la comunicación WebSocket.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class MessageEnvelope {
    // JOIN|OFFER|ANSWER|ICE_CANDIDATE|RTC_CONNECTED|HEARTBEAT|LEAVE|END|ERROR|PEER_JOINED|PEER_LEFT
    String type;
    String sessionId;
    String reservationId;
    String from;
    String to;
    Object payload;
    long ts;
    String traceId;

    public MessageEnvelope() {
        // Constructor vacío necesario para Jackson
    }
}
