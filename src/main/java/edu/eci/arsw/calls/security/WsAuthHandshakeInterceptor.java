package edu.eci.arsw.calls.security;

import org.springframework.http.server.*;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Interceptor para la autenticación de WebSocket mediante tokens.
 */
@Component
public class WsAuthHandshakeInterceptor implements HandshakeInterceptor {

    private final AuthorizationService authorizationService;

    public WsAuthHandshakeInterceptor(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    /**
     * Intercepta el handshake para autenticar al usuario mediante el token JWT.
     * * @param request    La solicitud HTTP entrante.
     * @param response   La respuesta HTTP.
     * @param wsHandler  El manejador de WebSocket.
     * @param attributes Los atributos para compartir datos entre interceptores y
     * manejadores.
     * @return true si el handshake debe continuar, false en caso contrario.
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
            attributes.put("token", token);
            return true;
        }
        return false;
    }

    /**
     * Método llamado después del handshake.
     * * @param request   La solicitud HTTP entrante.
     * @param response  La respuesta HTTP.
     * @param wsHandler El manejador de WebSocket.
     * @param exception Cualquier excepción que haya ocurrido durante el handshake.
     */
    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception) {
        // Este método se deja vacío intencionalmente ya que no se requiere
    }
}