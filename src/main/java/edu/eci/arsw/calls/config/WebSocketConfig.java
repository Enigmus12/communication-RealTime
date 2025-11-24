package edu.eci.arsw.calls.config;

import edu.eci.arsw.calls.security.WsAuthHandshakeInterceptor;
import edu.eci.arsw.calls.ws.CallWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * Configuración de WebSocket para la aplicación
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final CallWebSocketHandler handler;
    private final WsAuthHandshakeInterceptor interceptor;

    @Value("${app.ws.max-message-size:65536}")
    private int maxMessageSize;

    @Value("${app.ws.idle-timeout:30}")
    private long idleTimeout;

    public WebSocketConfig(CallWebSocketHandler handler, WsAuthHandshakeInterceptor interceptor) {
        this.handler = handler;
        this.interceptor = interceptor;
    }

    /**
     * Registrar los manejadores de WebSocket
     */
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/call")
                .addInterceptors(interceptor)

                .setAllowedOriginPatterns("*");
    }

    /**
     * Configurar el contenedor del servidor WebSocket
     */
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxTextMessageBufferSize(maxMessageSize);
        container.setMaxBinaryMessageBufferSize(maxMessageSize);
        container.setMaxSessionIdleTimeout(idleTimeout * 1000L); // Multiplicar por 1000 para MS
        return container;
    }
}