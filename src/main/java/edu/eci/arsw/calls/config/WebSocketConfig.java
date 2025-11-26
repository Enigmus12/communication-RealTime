package edu.eci.arsw.calls.config;

import edu.eci.arsw.calls.ws.CallWebSocketHandler;
import edu.eci.arsw.calls.security.WsAuthHandshakeInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final CallWebSocketHandler handler;
    private final WsAuthHandshakeInterceptor interceptor;

    @Value("${app.ws.max-message-size:65536}") private int maxMessageSize;
    @Value("${WS_IDLE_TIMEOUT_SECONDS:30}") private long idleTimeout;

    public WebSocketConfig(CallWebSocketHandler handler, WsAuthHandshakeInterceptor interceptor) {
        this.handler = handler; this.interceptor = interceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/call")
                .addInterceptors(interceptor)
                .setAllowedOriginPatterns("*");
    }

    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean c = new ServletServerContainerFactoryBean();
        c.setMaxTextMessageBufferSize(maxMessageSize);
        c.setMaxBinaryMessageBufferSize(maxMessageSize);
        c.setMaxSessionIdleTimeout(idleTimeout * 1000L);
        return c;
    }
}
