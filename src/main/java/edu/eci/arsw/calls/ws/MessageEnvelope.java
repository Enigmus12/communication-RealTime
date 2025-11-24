package edu.eci.arsw.calls.ws;

/**
 * Sobre de mensaje para comunicación WebSocket
 */
public class MessageEnvelope {
    public String type; // JOIN|OFFER|ANSWER|ICE_CANDIDATE|HEARTBEAT|LEAVE|END|ERROR
    public String sessionId;
    public String reservationId;
    public String from;
    public String to; // peer|userId|broadcast
    public Object payload;
    public long ts;
    public String traceId;
}
