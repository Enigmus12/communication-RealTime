package edu.eci.arsw.calls.security;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;
/**
 * Interceptor de handshake para autenticación WebSocket
 */
@Component
public class WsAuthHandshakeInterceptor implements HandshakeInterceptor {

    private final AuthorizationService authorizationService;

    public WsAuthHandshakeInterceptor(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    /**
     * Intercepta el handshake para autenticar la conexión WebSocket
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletReq) {
            var httpReq = servletReq.getServletRequest();
            String token = httpReq.getParameter("token");
            if (token == null || token.isBlank())
                return false;

            var info = authorizationService.parseTokenOrCookie(token, httpReq.getCookies());
            attributes.put("userId", info.userId());
            attributes.put("roles", info.roles());
            //  guarda el raw token para reenviarlo al scheduler
            attributes.put("token", token);
            return true;
        }
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception) {
        // No action required
    }
}