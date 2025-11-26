package edu.eci.arsw.calls.ws;

public class MessageEnvelope {
    // JOIN|OFFER|ANSWER|ICE_CANDIDATE|RTC_CONNECTED|HEARTBEAT|LEAVE|END|ERROR|PEER_JOINED|PEER_LEFT
    public String type;
    public String sessionId;
    public String reservationId;
    public String from;
    public String to;
    public Object payload;
    public long ts;
    public String traceId;
}
