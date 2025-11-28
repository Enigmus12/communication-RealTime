package edu.eci.arsw.calls.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuthorizationServiceTest {

    private final AuthorizationService service = new AuthorizationService();

    private String buildJwt(String sub, List<String> roles, long expEpochSec) {
        String headerJson = "{\"alg\":\"none\",\"typ\":\"JWT\"}";
        String rolesPart = roles == null || roles.isEmpty()
                ? ""
                : "\"roles\":[\"" + String.join("\",\"", roles) + "\"],";
        String payloadJson = String.format("{\"sub\":\"%s\",%s\"exp\":%d}", sub, rolesPart, expEpochSec);

        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".signature";
    }

    // ---------------------------------------------------------------------
    // parseBearer
    // ---------------------------------------------------------------------

    @Test
    void parseBearer_deberiaParsearTokenValido_casoFeliz1() {
        long exp = Instant.now().getEpochSecond() + 3600;
        String jwt = buildJwt("user-1", List.of("STUDENT"), exp);

        AuthorizationService.AuthInfo info = service.parseBearer("Bearer " + jwt);

        assertEquals("user-1", info.userId());
        assertEquals(List.of("STUDENT"), info.roles());
    }

    @Test
    void parseBearer_deberiaAceptarTokenSinPrefijo_casoFeliz2() {
        long exp = Instant.now().getEpochSecond() + 3600;
        String jwt = buildJwt("user-2", List.of(), exp);

        AuthorizationService.AuthInfo info = service.parseBearer(jwt);

        assertEquals("user-2", info.userId());
        assertTrue(info.roles().isEmpty());
    }

    @Test
    void parseBearer_noDeberiaPasar_cuandoBearerEsNulo() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.parseBearer(null));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Falta Authorization"));
    }

    @Test
    void parseBearer_noDeberiaPasar_cuandoJwtEsInvalido() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.parseBearer("no-es-un-jwt"));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("Token inválido", ex.getReason());
    }

    // ---------------------------------------------------------------------
    // parseTokenOrCookie
    // ---------------------------------------------------------------------

    @Test
    void parseTokenOrCookie_deberiaUsarTokenDirecto_casoFeliz1() {
        long exp = Instant.now().getEpochSecond() + 3600;
        String jwt = buildJwt("user-3", List.of("TUTOR"), exp);

        AuthorizationService.AuthInfo info =
                service.parseTokenOrCookie(jwt, null);

        assertEquals("user-3", info.userId());
        assertEquals(List.of("TUTOR"), info.roles());
    }

    @Test
    void parseTokenOrCookie_deberiaTomarDeCookie_casoFeliz2() {
        long exp = Instant.now().getEpochSecond() + 3600;
        String jwt = buildJwt("user-4", List.of("STUDENT"), exp);
        Cookie cookie = new Cookie("access_token", jwt);

        AuthorizationService.AuthInfo info =
                service.parseTokenOrCookie(null, new Cookie[]{cookie});

        assertEquals("user-4", info.userId());
    }

    @Test
    void parseTokenOrCookie_noDeberiaPasar_sinTokenNiCookies() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.parseTokenOrCookie(null, null));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("Falta token", ex.getReason());
    }

    @Test
    void parseTokenOrCookie_noDeberiaPasar_sinCookieAccessToken() {
        Cookie other = new Cookie("other", "value");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.parseTokenOrCookie(null, new Cookie[]{other}));

        assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatusCode());
        assertEquals("Falta token", ex.getReason());
    }
}
