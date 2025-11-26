package edu.eci.arsw.calls.ws;

import org.springframework.web.socket.WebSocketSession;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionRegistry {
    private final Map<String, Map<String, WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public void register(String sessionId, String userId, WebSocketSession ws) {
        sessions.computeIfAbsent(sessionId, k -> new ConcurrentHashMap<>()).put(userId, ws);
    }
    public void unregister(String sessionId, String userId) {
        Map<String, WebSocketSession> map = sessions.get(sessionId);
        if (map != null) map.remove(userId);
    }
    public Map<String, WebSocketSession> get(String sessionId) {
        return sessions.getOrDefault(sessionId, Map.of());
    }
    public Map<String, Map<String, WebSocketSession>> all() { return sessions; }
}
