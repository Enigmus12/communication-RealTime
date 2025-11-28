package edu.eci.arsw.calls.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TokenAuthFilterTest {

    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final TokenAuthFilter filter = new TokenAuthFilter(authorizationService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_deberiaAutenticarUsuario_casoFeliz1() throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer AAA");
        when(authorizationService.parseBearer("Bearer AAA"))
                .thenReturn(new AuthorizationService.AuthInfo("user-1", List.of("student", "tutor")));

        filter.doFilterInternal(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("user-1", SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        assertEquals(2, SecurityContextHolder.getContext().getAuthentication().getAuthorities().size());

        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_deberiaContinuarSinAutenticar_sinBearer_casoFeliz2() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);

        filter.doFilterInternal(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
        verifyNoInteractions(authorizationService);
    }

    @Test
    void doFilterInternal_noDeberiaPasar_cuandoParseBearerFalla() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer BAD");
        when(authorizationService.parseBearer("Bearer BAD"))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Token inválido"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> filter.doFilterInternal(request, response, chain));

        assertEquals("Token inválido", ex.getReason());
        verify(chain, never()).doFilter(any(), any());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilterInternal_deberiaAutenticarSinRoles_casoBorde() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Bearer NO_ROLES");
        when(authorizationService.parseBearer("Bearer NO_ROLES"))
                .thenReturn(new AuthorizationService.AuthInfo("user-2", List.of()));

        filter.doFilterInternal(request, response, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertTrue(SecurityContextHolder.getContext().getAuthentication().getAuthorities().isEmpty());
        verify(chain).doFilter(request, response);
    }
}
