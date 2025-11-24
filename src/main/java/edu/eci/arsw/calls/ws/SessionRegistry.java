package edu.eci.arsw.calls.ws;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro de sesiones WebSocket por sesión de llamada y usuario
 */
@Component
public class SessionRegistry {
    private final Map<String, Map<String, WebSocketSession>> sessions = new ConcurrentHashMap<>();

    /**
     * Registra una sesión WebSocket para un usuario en una sesión de llamada
     * 
     * @param sessionId ID de la sesión de llamada
     * @param userId    ID del usuario
     * @param ws        Sesión WebSocket
     */
    public void register(String sessionId, String userId, WebSocketSession ws) {
        sessions.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>()).put(userId, ws);
    }

    /**
     * Desregistra una sesión WebSocket para un usuario en una sesión de llamada
     * 
     * @param sessionId ID de la sesión de llamada
     * @param userId    ID del usuario
     */
    public void unregister(String sessionId, String userId) {
        Map<String, WebSocketSession> map = sessions.get(sessionId);
        if (map != null)
            map.remove(userId);
    }

    /**
     * Obtiene las sesiones WebSocket para una sesión de llamada
     * 
     * @param sessionId ID de la sesión de llamada
     * @return Mapa de ID de usuario a sesión WebSocket
     */
    public Map<String, WebSocketSession> get(String sessionId) {
        return sessions.getOrDefault(sessionId, java.util.Map.of());
    }

    /**
     * Obtiene todas las sesiones registradas
     * 
     * @return Mapa de ID de sesión de llamada a mapa de ID de usuario y sesión
     *         WebSocket
     */
    public Map<String, Map<String, WebSocketSession>> all() {
        return sessions;
    }
}
