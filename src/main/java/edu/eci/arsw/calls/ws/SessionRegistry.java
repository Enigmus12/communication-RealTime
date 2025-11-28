package edu.eci.arsw.calls.ws;

import org.springframework.web.socket.WebSocketSession;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro de sesiones WebSocket.
 */
@Component
public class SessionRegistry {
    private final Map<String, Map<String, WebSocketSession>> sessions = new ConcurrentHashMap<>();

    /**
     * Registra una sesión WebSocket para un usuario en una sesión de llamada.
     * 
     * @param sessionId ID de la sesión de llamada.
     * @param userId    ID del usuario.
     * @param ws        Sesión WebSocket del usuario.
     */
    public void register(String sessionId, String userId, WebSocketSession ws) {
        sessions.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>()).put(userId, ws);
    }

    /**
     * Elimina el registro de una sesión WebSocket para un usuario en una sesión de
     * llamada.
     * 
     * @param sessionId ID de la sesión de llamada.
     * @param userId    ID del usuario.
     */
    public void unregister(String sessionId, String userId) {
        Map<String, WebSocketSession> map = sessions.get(sessionId);
        if (map != null)
            map.remove(userId);
    }

    /**
     * Obtiene las sesiones WebSocket registradas para una sesión de llamada.
     * 
     * @param sessionId ID de la sesión de llamada.
     * @return Mapa de IDs de usuario a sus sesiones WebSocket.
     */
    public Map<String, WebSocketSession> get(String sessionId) {
        return sessions.getOrDefault(sessionId, Map.of());
    }

    /**
     * Obtiene todas las sesiones WebSocket registradas.
     * 
     * @return Mapa de IDs de sesión de llamada a mapas de IDs de usuario y sus
     *         sesiones WebSocket.
     */
    public Map<String, Map<String, WebSocketSession>> all() {
        return sessions;
    }
}
