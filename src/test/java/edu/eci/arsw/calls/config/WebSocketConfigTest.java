package edu.eci.arsw.calls.config;

import edu.eci.arsw.calls.security.WsAuthHandshakeInterceptor;
import edu.eci.arsw.calls.ws.CallWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

/**
 * Pruebas de WebSocketConfig.
 */
class WebSocketConfigTest {

    private CallWebSocketHandler handler;
    private WsAuthHandshakeInterceptor interceptor;
    private WebSocketConfig config;

    @BeforeEach
    void setUp() {
        handler = mock(CallWebSocketHandler.class);
        interceptor = mock(WsAuthHandshakeInterceptor.class);

        config = new WebSocketConfig(handler, interceptor);

        ReflectionTestUtils.setField(config, "maxMessageSize", 1024);
        ReflectionTestUtils.setField(config, "idleTimeout", 60L);
    }

    @Test
    void registerWebSocketHandlers_deberiaRegistrarHandlerConInterceptorYOrigenes() {
        WebSocketHandlerRegistry registry = mock(WebSocketHandlerRegistry.class);
        WebSocketHandlerRegistration registration = mock(WebSocketHandlerRegistration.class);

        when(registry.addHandler(handler, "/ws/call")).thenReturn(registration);
        when(registration.addInterceptors(interceptor)).thenReturn(registration);
        when(registration.setAllowedOriginPatterns("*")).thenReturn(registration);

        config.registerWebSocketHandlers(registry);

        verify(registry).addHandler(handler, "/ws/call");
        verify(registration).addInterceptors(interceptor);
        verify(registration).setAllowedOriginPatterns("*");
    }

    @Test
    void createWebSocketContainer_deberiaConfigurarTamanosYTimeout() {
        ServletServerContainerFactoryBean bean = config.createWebSocketContainer();

        assertEquals(1024, bean.getMaxTextMessageBufferSize());
        assertEquals(1024, bean.getMaxBinaryMessageBufferSize());
        assertEquals(60_000L, bean.getMaxSessionIdleTimeout());
    }
}
