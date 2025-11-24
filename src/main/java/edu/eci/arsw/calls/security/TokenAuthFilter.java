package edu.eci.arsw.calls.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.stream.Collectors;
/**
 * Filtro de autenticación basado en tokens Bearer
 */
@Component
public class TokenAuthFilter extends OncePerRequestFilter {
    private final AuthorizationService authorizationService;

    public TokenAuthFilter(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    /**
     * Procesa la autenticación basada en tokens Bearer
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            String bearer = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (bearer != null) {
                var info = authorizationService.parseBearer(bearer);
                var authorities = info.roles().stream()
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r.toUpperCase()))
                        .collect(Collectors.toList());
                var auth = new UsernamePasswordAuthenticationToken(info.userId(), "jwt", authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
                MDC.put("userId", info.userId());
            }
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
