package com.proyecto.servicios.onboarding.security.sesion;

import com.proyecto.servicios.onboarding.dto.response.SesionActivaResponse;
import com.proyecto.servicios.onboarding.entity.SesionActiva;
import com.proyecto.servicios.onboarding.exception.SesionDuplicadaException;
import com.proyecto.servicios.onboarding.exception.SesionNoEncontradaException;
import com.proyecto.servicios.onboarding.repository.SesionActivaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Gestion de sesiones activas server-side. Cada JWT emitido tiene un jti unico
 * y un row en sesiones_activas; al pasar activa=false el token queda revocado
 * aunque no haya expirado por fecha.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SesionServiceImpl implements SesionService {

    private final SesionActivaRepository repo;
    private final Clock clock;

    @Override
    @Transactional
    public SesionActiva crearSesion(Long clienteId, String jti, String userAgent, String ipAddress, boolean forceLogin) {
        List<SesionActiva> activasPrevias = repo.findByClienteIdAndActivaTrueOrderByFechaCreacionDesc(clienteId);

        if (!activasPrevias.isEmpty()) {
            if (!forceLogin) {
                List<SesionActivaResponse> detalles = activasPrevias.stream()
                        .map(s -> toResponse(s, null))
                        .toList();
                log.info("Login denegado por sesion duplicada clienteId={} sesionesPrevias={}",
                        clienteId, activasPrevias.size());
                throw new SesionDuplicadaException(detalles);
            }
            int cerradas = repo.cerrarPorCliente(clienteId, "FORZADO_POR_NUEVO_LOGIN", OffsetDateTime.now(clock));
            log.info("Force-login: cerradas {} sesiones previas clienteId={}", cerradas, clienteId);
        }

        OffsetDateTime ahora = OffsetDateTime.now(clock);
        SesionActiva nueva = SesionActiva.builder()
                .clienteId(clienteId)
                .jti(jti)
                .dispositivo(detectarDispositivo(userAgent))
                .userAgent(truncar(userAgent, 500))
                .ipAddress(truncar(ipAddress, 50))
                .activa(Boolean.TRUE)
                .fechaCreacion(ahora)
                .ultimaActividad(ahora)
                .build();
        SesionActiva saved = repo.save(nueva);
        log.info("Sesion creada id={} clienteId={} dispositivo={}", saved.getId(), clienteId, saved.getDispositivo());
        return saved;
    }

    @Override
    @Transactional
    public void cerrarPorJti(String jti) {
        repo.findByJtiAndActivaTrue(jti).ifPresent(s -> {
            s.setActiva(Boolean.FALSE);
            s.setMotivoCierre("LOGOUT");
            s.setFechaCierre(OffsetDateTime.now(clock));
            repo.save(s);
            log.info("Logout sesion id={} clienteId={}", s.getId(), s.getClienteId());
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<SesionActivaResponse> listarActivas(Long clienteId, String jtiActual) {
        return repo.findByClienteIdAndActivaTrueOrderByFechaCreacionDesc(clienteId).stream()
                .map(s -> toResponse(s, jtiActual))
                .toList();
    }

    @Override
    @Transactional
    public void cerrarPorId(Long sesionId, Long clienteId) {
        SesionActiva s = repo.findById(sesionId)
                .orElseThrow(SesionNoEncontradaException::new);
        if (!s.getClienteId().equals(clienteId)) {
            // No revelamos la existencia a otros clientes
            throw new SesionNoEncontradaException();
        }
        if (Boolean.TRUE.equals(s.getActiva())) {
            s.setActiva(Boolean.FALSE);
            s.setMotivoCierre("CERRADA_POR_USUARIO");
            s.setFechaCierre(OffsetDateTime.now(clock));
            repo.save(s);
            log.info("Sesion cerrada por usuario id={} clienteId={}", sesionId, clienteId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public boolean estaActiva(String jti) {
        return repo.findByJtiAndActivaTrue(jti).isPresent();
    }

    @Override
    @Transactional
    public void tocarActividad(String jti) {
        repo.tocarActividad(jti, OffsetDateTime.now(clock));
    }

    // ===== helpers =====

    private SesionActivaResponse toResponse(SesionActiva s, String jtiActual) {
        boolean esActual = jtiActual != null && jtiActual.equals(s.getJti());
        return new SesionActivaResponse(
                s.getId(), s.getDispositivo(), s.getIpAddress(),
                s.getFechaCreacion(), s.getUltimaActividad(), esActual);
    }

    private String detectarDispositivo(String ua) {
        if (ua == null || ua.isBlank()) return "desconocido";
        String l = ua.toLowerCase();
        String sistema = l.contains("android") ? "android"
                : l.contains("iphone") || l.contains("ipad") ? "ios"
                : l.contains("windows") ? "windows"
                : l.contains("mac") ? "mac"
                : l.contains("linux") ? "linux" : "desconocido";
        String canal = (l.contains("dart") || l.contains("flutter") || l.contains("okhttp")) ? "movil"
                : l.contains("postman") ? "postman"
                : "web";
        String nav = l.contains("edg/") ? "edge"
                : l.contains("chrome/") ? "chrome"
                : l.contains("firefox/") ? "firefox"
                : l.contains("safari/") && canal.equals("web") ? "safari" : "";
        return canal + (nav.isEmpty() ? "" : "-" + nav) + "-" + sistema;
    }

    private String truncar(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
