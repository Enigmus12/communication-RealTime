package edu.eci.arsw.calls.ws;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MessageEnvelopeTest {

    @Test
    void constructorDeberiaPermitirAsignarCampos() {
        MessageEnvelope env = new MessageEnvelope();
        env.type = "JOIN";
        env.sessionId = "SID-1";
        env.reservationId = "RES-1";
        env.from = "U1";
        env.to = "U2";
        env.payload = "data";
        env.ts = 123L;
        env.traceId = "trace-1";

        assertEquals("JOIN", env.type);
        assertEquals("SID-1", env.sessionId);
        assertEquals("RES-1", env.reservationId);
        assertEquals("U1", env.from);
        assertEquals("U2", env.to);
        assertEquals("data", env.payload);
        assertEquals(123L, env.ts);
        assertEquals("trace-1", env.traceId);
    }
}
