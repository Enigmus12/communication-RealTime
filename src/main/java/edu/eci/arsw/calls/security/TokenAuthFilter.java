package edu.eci.arsw.calls.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtro de autenticación basado en tokens JWT.
 */
@Component
public class TokenAuthFilter extends OncePerRequestFilter {
    private final AuthorizationService authorizationService;

    public TokenAuthFilter(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    /**
     * Procesa cada solicitud HTTP para autenticar al usuario mediante el token JWT.
     *
     * @param request     La solicitud HTTP entrante.
     * @param response    La respuesta HTTP.
     * @param filterChain La cadena de filtros para continuar el procesamiento.
     * @throws ServletException Si ocurre un error en el servlet.
     * @throws IOException      Si ocurre un error de E/S.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String bearer = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (bearer != null) {
                var info = authorizationService.parseBearer(bearer);

                var authorities = info.roles().stream()
                        .map(r -> new SimpleGrantedAuthority("ROLE_" + r.toUpperCase()))
                        .toList(); 

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
