package edu.eci.arsw.calls.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.socket.WebSocketHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WsAuthHandshakeInterceptorTest {

    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final WsAuthHandshakeInterceptor interceptor = new WsAuthHandshakeInterceptor(authorizationService);

    @Test
    void beforeHandshake_deberiaAceptarTokenValido_casoFeliz1() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setParameter("token", "AAA");
        servletRequest.setCookies(new Cookie("access_token", "AAA"));

        ServletServerHttpRequest request = new ServletServerHttpRequest(servletRequest);
        ServerHttpResponse response = mock(ServerHttpResponse.class);
        WebSocketHandler handler = mock(WebSocketHandler.class);
        Map<String, Object> attributes = new HashMap<>();

        when(authorizationService.parseTokenOrCookie(eq("AAA"), any()))
                .thenReturn(new AuthorizationService.AuthInfo("user-1", List.of("STUDENT")));

        boolean result = interceptor.beforeHandshake(request, response, handler, attributes);

        assertTrue(result);
        assertEquals("user-1", attributes.get("userId"));
        assertEquals(List.of("STUDENT"), attributes.get("roles"));
        assertEquals("AAA", attributes.get("token"));
    }

    @Test
    void beforeHandshake_noDeberiaPasar_sinTokenQuery_casoNoFeliz1() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        // sin parámetro "token"
        ServletServerHttpRequest request = new ServletServerHttpRequest(servletRequest);

        boolean result = interceptor.beforeHandshake(request, mock(ServerHttpResponse.class),
                mock(WebSocketHandler.class), new HashMap<>());

        assertFalse(result);
        verifyNoInteractions(authorizationService);
    }

    @Test
    void beforeHandshake_noDeberiaPasar_cuandoRequestNoEsServlet_casoNoFeliz2() {
        ServerHttpRequest request = mock(ServerHttpRequest.class); // NO es ServletServerHttpRequest

        boolean result = interceptor.beforeHandshake(request, mock(ServerHttpResponse.class),
                mock(WebSocketHandler.class), new HashMap<>());

        assertFalse(result);
        verifyNoInteractions(authorizationService);
    }

    @Test
    void beforeHandshake_noDeberiaPasar_cuandoAuthorizationServiceLanzaExcepcion() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setParameter("token", "BAD");
        ServletServerHttpRequest request = new ServletServerHttpRequest(servletRequest);

        when(authorizationService.parseTokenOrCookie(eq("BAD"), any()))
                .thenThrow(new RuntimeException("boom"));

        assertThrows(RuntimeException.class, () ->
                interceptor.beforeHandshake(request, mock(ServerHttpResponse.class),
                        mock(WebSocketHandler.class), new HashMap<>()));
    }
}
