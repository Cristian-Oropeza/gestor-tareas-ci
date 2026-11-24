package com.proyecto.servicios.onboarding.security.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.onboarding.dto.response.GenericResponse;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting por IP con Bucket4j in-memory.
 *
 * Dos limites simultaneos:
 *  - Login/verificar-2FA: {loginPor15Min} intentos en 15 min por IP (anti brute-force).
 *  - General:            {ipPorMinuto} requests por minuto por IP (mitigacion DoS basico).
 *
 * Al excederse, responde 429 con GenericResponse (no entra al chain).
 *
 * In-memory es suficiente para una sola instancia; en multi-instancia se
 * reemplaza por bucket4j-redis sin tocar los controllers.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private static final List<String> RUTAS_LOGIN = List.of(
            "/api/v1/auth/login", "/api/v1/auth/2fa/verificar"
    );

    private final RateLimitProperties props;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, Bucket> loginBuckets   = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> generalBuckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (!props.isEnabled()) {
            chain.doFilter(req, res);
            return;
        }

        String ip = extraerIp(req);
        boolean esLogin = esRutaLogin(req.getRequestURI());

        Bucket bucket = esLogin
                ? loginBuckets.computeIfAbsent(ip, k -> construirLoginBucket())
                : generalBuckets.computeIfAbsent(ip, k -> construirGeneralBucket());

        if (bucket.tryConsume(1)) {
            chain.doFilter(req, res);
        } else {
            log.warn("Rate limit excedido ip={} login={} uri={}", ip, esLogin, req.getRequestURI());
            escribir429(res, esLogin);
        }
    }

    private Bucket construirLoginBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(props.getLoginPor15Min())
                        .refillGreedy(props.getLoginPor15Min(), Duration.ofMinutes(15))
                        .build())
                .build();
    }

    private Bucket construirGeneralBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(props.getIpPorMinuto())
                        .refillGreedy(props.getIpPorMinuto(), Duration.ofMinutes(1))
                        .build())
                .build();
    }

    private boolean esRutaLogin(String uri) {
        if (uri == null) return false;
        return RUTAS_LOGIN.stream().anyMatch(uri::equals);
    }

    private String extraerIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int coma = xff.indexOf(',');
            return (coma > 0 ? xff.substring(0, coma) : xff).trim();
        }
        return req.getRemoteAddr();
    }

    private void escribir429(HttpServletResponse res, boolean esLogin) throws IOException {
        res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding("UTF-8");
        String mensaje = esLogin
                ? "Demasiados intentos de autenticacion. Espera 15 minutos."
                : "Demasiadas peticiones desde tu IP. Espera 1 minuto.";
        GenericResponse body = GenericResponse.builder()
                .codigo(HttpStatus.TOO_MANY_REQUESTS.value())
                .mensaje(mensaje)
                .build();
        res.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
