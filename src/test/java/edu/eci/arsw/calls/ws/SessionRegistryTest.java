package edu.eci.arsw.calls.ws;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionRegistryTest {

    // -------------------------------------------------------------------------
    // register(String sessionId, String userId, WebSocketSession ws)
    // -------------------------------------------------------------------------

    @Test
    void registerDeberiaGuardarSesionCasoFeliz1() {
        SessionRegistry registry = new SessionRegistry();
        WebSocketSession ws = mock(WebSocketSession.class);

        registry.register("S1", "U1", ws);

        Map<String, WebSocketSession> map = registry.get("S1");
        assertEquals(1, map.size());
        assertSame(ws, map.get("U1"));
    }

    @Test
    void register_deberiaSobrescribirSesionMismoUsuario_casoFeliz2() {
        SessionRegistry registry = new SessionRegistry();
        WebSocketSession ws1 = mock(WebSocketSession.class);
        WebSocketSession ws2 = mock(WebSocketSession.class);

        registry.register("S1", "U1", ws1);
        registry.register("S1", "U1", ws2);

        Map<String, WebSocketSession> map = registry.get("S1");
        assertEquals(1, map.size());
        assertSame(ws2, map.get("U1"));
    }

    @Test
    void register_noDeberiaPasar_cuandoSessionIdEsNull() {
        SessionRegistry registry = new SessionRegistry();
        WebSocketSession ws = mock(WebSocketSession.class);

        assertThrows(NullPointerException.class,
                () -> registry.register(null, "U1", ws));
    }

    @Test
    void register_noDeberiaPasar_cuandoUserIdEsNull() {
        SessionRegistry registry = new SessionRegistry();
        WebSocketSession ws = mock(WebSocketSession.class);

        assertThrows(NullPointerException.class,
                () -> registry.register("S1", null, ws));
    }

    // -------------------------------------------------------------------------
    // unregister(String sessionId, String userId)
    // -------------------------------------------------------------------------

    @Test
    void unregister_deberiaEliminarSesion_casoFeliz1() {
        SessionRegistry registry = new SessionRegistry();
        WebSocketSession ws = mock(WebSocketSession.class);
        registry.register("S1", "U1", ws);

        registry.unregister("S1", "U1");

        assertTrue(registry.get("S1").isEmpty());
    }

    @Test
    void unregister_deberiaIgnorarUsuarioInexistente_casoFeliz2() {
        SessionRegistry registry = new SessionRegistry();
        WebSocketSession ws = mock(WebSocketSession.class);
        registry.register("S1", "U1", ws);

        registry.unregister("S1", "U2");

        assertEquals(1, registry.get("S1").size());
    }

    @Test
    void unregister_noDeberiaPasar_cuandoSessionIdEsNull() {
        SessionRegistry registry = new SessionRegistry();

        assertThrows(NullPointerException.class,
                () -> registry.unregister(null, "U1"));
    }

    @Test
    void unregister_noDeberiaPasar_cuandoSessionIdNoExiste_noHaceNada() {
        SessionRegistry registry = new SessionRegistry();
        var ws = mock(org.springframework.web.socket.WebSocketSession.class);

        registry.register("S1", "U1", ws);
        assertEquals(1, registry.get("S1").size());

        assertDoesNotThrow(() -> registry.unregister("S2", "U1"));
        assertEquals(1, registry.get("S1").size());
    }

    // -------------------------------------------------------------------------
    // get(String sessionId)
    // -------------------------------------------------------------------------

    @Test
    void get_deberiaRetornarMapaVacioParaSesionNueva_casoFeliz1() {
        SessionRegistry registry = new SessionRegistry();

        Map<String, WebSocketSession> map = registry.get("NO-SESSION");

        assertNotNull(map);
        assertTrue(map.isEmpty());
    }

    @Test
    void get_deberiaRetornarMapaConUsuariosRegistrados_casoFeliz2() {
        SessionRegistry registry = new SessionRegistry();
        WebSocketSession ws = mock(WebSocketSession.class);
        registry.register("S1", "U1", ws);

        Map<String, WebSocketSession> map = registry.get("S1");

        assertEquals(1, map.size());
    }

    @Test
    void get_noDeberiaPasar_cuandoSessionIdEsNull() {
        SessionRegistry registry = new SessionRegistry();

        assertThrows(NullPointerException.class,
                () -> registry.get(null));
    }

    @Test
    void get_noDeberiaPasar_cuandoSeModificaMapaDevueltoAfectaInterno() {
        SessionRegistry registry = new SessionRegistry();
        WebSocketSession ws = mock(WebSocketSession.class);
        registry.register("S1", "U1", ws);
        assertEquals(1, registry.get("S1").size());

        Map<String, WebSocketSession> map = registry.get("S1");
        map.clear();
        assertEquals(0, registry.get("S1").size());
    }

    // -------------------------------------------------------------------------
    // all()
    // -------------------------------------------------------------------------

    @Test
    void all_deberiaRetornarTodasLasSesiones_casoFeliz1() {
        SessionRegistry registry = new SessionRegistry();
        registry.register("S1", "U1", mock(WebSocketSession.class));
        registry.register("S2", "U2", mock(WebSocketSession.class));

        Map<String, Map<String, WebSocketSession>> all = registry.all();

        assertEquals(2, all.size());
        assertTrue(all.containsKey("S1"));
        assertTrue(all.containsKey("S2"));
    }

    @Test
    void all_deberiaRetornarMapaVacioSinSesiones_casoFeliz2() {
        SessionRegistry registry = new SessionRegistry();

        Map<String, Map<String, WebSocketSession>> all = registry.all();

        assertNotNull(all);
        assertTrue(all.isEmpty());
    }

    @Test
    void all_noDeberiaPasar_cuandoSeModificaMapaDevuelto_rompeEstadoInterno() {
        SessionRegistry registry = new SessionRegistry();
        registry.register("S1", "U1", mock(WebSocketSession.class));

        // sanity check
        assertFalse(registry.all().isEmpty());

        Map<String, Map<String, WebSocketSession>> all = registry.all();
        all.clear(); 

        assertTrue(registry.all().isEmpty());
    }

    @Test
    void all_noDeberiaPasar_cuandoSeModificaMapaInternoDeUnaSesion_rompeUsuarios() {
        SessionRegistry registry = new SessionRegistry();
        registry.register("S1", "U1", mock(WebSocketSession.class));

        // sanity check
        assertEquals(1, registry.get("S1").size());

        Map<String, Map<String, WebSocketSession>> all = registry.all();
        Map<String, WebSocketSession> inner = all.get("S1");
        inner.clear(); 
        assertEquals(0, registry.get("S1").size());
    }
}
