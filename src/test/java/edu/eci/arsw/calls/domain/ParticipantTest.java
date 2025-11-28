package edu.eci.arsw.calls.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ParticipantTest {

    @Test
    void gettersYSetters_deberianFuncionar() {
        Participant p = new Participant();
        p.setUserId("U1");
        p.setRole("STUDENT");
        p.setJoinedAt(100L);
        p.setLeftAt(200L);

        assertEquals("U1", p.getUserId());
        assertEquals("STUDENT", p.getRole());
        assertEquals(100L, p.getJoinedAt());
        assertEquals(200L, p.getLeftAt());
    }

    @Test
    void constructorConArgumentos_deberiaInicializarCampos() {
        Participant p = new Participant("U2", "TUTOR", 50L);

        assertEquals("U2", p.getUserId());
        assertEquals("TUTOR", p.getRole());
        assertEquals(50L, p.getJoinedAt());
        assertNull(p.getLeftAt());
    }
}
