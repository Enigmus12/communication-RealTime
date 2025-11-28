package edu.eci.arsw.calls.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.*;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.*;

/**
 * Representa una sesión de llamada en la aplicación.
 */
@Document(collection = "callSessions")
@CompoundIndex(name = "status_created_idx", def = "{'status':1,'createdAt':-1}")
public class CallSession {
    @Id
    private String id;

    @Indexed(unique = true)
    private String sessionId;
    @Indexed(unique = true)
    private String reservationId;

    private List<Participant> participants = new ArrayList<>();

    // CREATED|CONNECTING|CONNECTED|ENDED|EXPIRED
    private String status;
    private long createdAt;
    private Long connectedAt;
    private Long endedAt;

    private boolean turnUsed;
    private Metrics metrics = new Metrics();

    @Indexed(expireAfterSeconds = 0)
    private Instant ttl;

    public static CallSession create(String sessionId, String reservationId, Instant ttl) {
        CallSession cs = new CallSession();
        cs.sessionId = sessionId;
        cs.reservationId = reservationId;
        cs.status = "CREATED";
        cs.createdAt = System.currentTimeMillis();
        cs.ttl = ttl;
        return cs;
    }

    // getters/setters
    public String getId() {
        return id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getReservationId() {
        return reservationId;
    }

    public void setReservationId(String reservationId) {
        this.reservationId = reservationId;
    }

    public List<Participant> getParticipants() {
        return participants;
    }

    public void setParticipants(List<Participant> participants) {
        this.participants = participants;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public Long getConnectedAt() {
        return connectedAt;
    }

    public void setConnectedAt(Long connectedAt) {
        this.connectedAt = connectedAt;
    }

    public Long getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Long endedAt) {
        this.endedAt = endedAt;
    }

    public boolean isTurnUsed() {
        return turnUsed;
    }

    public void setTurnUsed(boolean turnUsed) {
        this.turnUsed = turnUsed;
    }

    public Metrics getMetrics() {
        return metrics;
    }

    public void setMetrics(Metrics metrics) {
        this.metrics = metrics;
    }

    public Instant getTtl() {
        return ttl;
    }

    public void setTtl(Instant ttl) {
        this.ttl = ttl;
    }
}
