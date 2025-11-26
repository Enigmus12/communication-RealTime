package edu.eci.arsw.calls.security;

import org.springframework.http.server.*;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.List;
import java.util.Map;

@Component
public class WsAuthHandshakeInterceptor implements HandshakeInterceptor {

    private final AuthorizationService authorizationService;

    public WsAuthHandshakeInterceptor(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        if (request instanceof ServletServerHttpRequest servletReq) {
            var httpReq = servletReq.getServletRequest();
            String token = httpReq.getParameter("token");
            if (token == null || token.isBlank()) return false;

            var info = authorizationService.parseTokenOrCookie(token, httpReq.getCookies());
            attributes.put("userId", info.userId());
            attributes.put("roles", info.roles());
            attributes.put("token", token); // se reenvía a scheduler
            return true;
        }
        return false;
    }

    @Override public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                         WebSocketHandler wsHandler, Exception exception) { }
}
