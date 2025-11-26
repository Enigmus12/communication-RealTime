package edu.eci.arsw.calls.security;

import com.fasterxml.jackson.databind.*;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
public class AuthorizationService {
    private final ObjectMapper om = new ObjectMapper();
    private static final String ROLES_CLAIM = "roles";

    public AuthInfo parseBearer(String bearer) {
        if (bearer == null || bearer.isBlank())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Falta Authorization");
        String token = bearer.trim();
        if (token.toLowerCase(Locale.ROOT).startsWith("bearer "))
            token = token.substring(7).trim();
        return parseJwt(token);
    }

    public AuthInfo parseTokenOrCookie(String token, Cookie[] cookies) {
        String bearer = null;
        if (token != null && !token.isBlank()) bearer = "Bearer " + token;
        if (bearer == null && cookies != null) {
            for (Cookie c : cookies) {
                if ("access_token".equals(c.getName())) { bearer = "Bearer " + c.getValue(); break; }
            }
        }
        if (bearer == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Falta token");
        return parseBearer(bearer);
    }

    private AuthInfo parseJwt(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) throw new IllegalArgumentException("JWT inválido");
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JsonNode payload = om.readTree(payloadJson);

            String userId = payload.has("sub") ? payload.get("sub").asText() : null;
            if (userId == null || userId.isBlank())
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT sin sub");

            List<String> roles = new ArrayList<>();
            if (payload.has(ROLES_CLAIM) && payload.get(ROLES_CLAIM).isArray()) {
                payload.get(ROLES_CLAIM).forEach(r -> roles.add(r.asText()));
            } else if (payload.has("role")) {
                roles.add(payload.get("role").asText());
            }

            if (payload.has("exp")) {
                long exp = payload.get("exp").asLong(0);
                if (exp > 0 && Instant.now().getEpochSecond() > exp)
                    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token expirado");
            }
            return new AuthInfo(userId, roles);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token inválido");
        }
    }

    public record AuthInfo(String userId, List<String> roles) {}
}
