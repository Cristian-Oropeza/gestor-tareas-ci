package com.proyecto.servicios.onboarding.security;

import com.proyecto.servicios.onboarding.security.sesion.SesionService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filtro JWT: si viene el header Authorization: Bearer <token> y es valido,
 * y el jti sigue activo server-side, lo pone en el SecurityContext y actualiza
 * ultima_actividad. Si no viene o el jti esta revocado, deja pasar sin autenticar
 * (SecurityConfig decide si eso vale 401).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final SesionService sesionService;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String auth = req.getHeader(HEADER);
        if (auth != null && auth.startsWith(PREFIX)) {
            String token = auth.substring(PREFIX.length()).trim();
            try {
                Claims claims = jwtService.validar(token);
                String correo = claims.getSubject();
                String jti = claims.getId();

                // Pre-auth tokens (scope=2fa-pending) NO llevan ancla en sesiones_activas:
                // son validos solo contra /auth/2fa/verificar y no pasan por este filtro con
                // intencion de autenticar. Si no hay jti, simplemente no autenticamos.
                if (jti == null || jti.isBlank()) {
                    chain.doFilter(req, res);
                    return;
                }

                if (!sesionService.estaActiva(jti)) {
                    log.debug("JWT con jti revocado o inactivo jti={}", jti);
                    chain.doFilter(req, res);
                    return;
                }

                sesionService.tocarActividad(jti);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                correo, null, List.of(new SimpleGrantedAuthority("ROLE_CLIENTE")));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException ex) {
                log.debug("JWT invalido: {}", ex.getMessage());
            }
        }
        chain.doFilter(req, res);
    }
}
