package com.proyecto.servicios.onboarding.service.impl;

import com.proyecto.servicios.onboarding.dto.request.LoginRequest;
import com.proyecto.servicios.onboarding.dto.request.Totp2FAVerificarRequest;
import com.proyecto.servicios.onboarding.dto.response.LoginResponse;
import com.proyecto.servicios.onboarding.entity.SesionActiva;
import com.proyecto.servicios.onboarding.entity.Usuario;
import com.proyecto.servicios.onboarding.exception.CredencialesInvalidasException;
import com.proyecto.servicios.onboarding.exception.PreAuthTokenInvalidoException;
import com.proyecto.servicios.onboarding.exception.TotpCodigoInvalidoException;
import com.proyecto.servicios.onboarding.exception.UsuarioInactivoException;
import com.proyecto.servicios.onboarding.repository.UsuarioRepository;
import com.proyecto.servicios.onboarding.security.JwtService;
import com.proyecto.servicios.onboarding.security.sesion.SesionProperties;
import com.proyecto.servicios.onboarding.security.sesion.SesionService;
import com.proyecto.servicios.onboarding.security.ratelimit.LockoutService;
import com.proyecto.servicios.onboarding.security.totp.TotpService;
import com.proyecto.servicios.onboarding.service.AuthService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SesionService sesionService;
    private final TotpService totpService;
    private final LockoutService lockoutService;
    private final SesionProperties sesionProps;

    // ===================================================================
    // Etapa 1: password + (si no hay 2FA) sesion + token
    // ===================================================================
    @Override
    @Transactional
    public LoginResponse login(LoginRequest req, boolean forceLogin, String userAgent, String ipAddress) {
        String correo = req.correo().toLowerCase().trim();
        // 1) Verificar lockout ANTES de validar password para no dar hints por timing.
        lockoutService.verificarNoBloqueado(correo);

        Usuario u = usuarioRepository.findByCorreo(correo)
                .orElseThrow(CredencialesInvalidasException::new);

        if (!Boolean.TRUE.equals(u.getActivo()) || !Boolean.TRUE.equals(u.getCliente().getActivo())) {
            throw new UsuarioInactivoException();
        }
        if (!passwordEncoder.matches(req.password(), u.getPassword())) {
            lockoutService.registrarFallo(correo);
            log.warn("Login fallido para correo={}", correo);
            throw new CredencialesInvalidasException();
        }
        lockoutService.registrarExito(correo);

        // ¿Tiene 2FA activo? Si si, devolvemos preAuthToken y NO creamos sesion.
        if (totpService.tieneActivo(correo)) {
            String preAuth = jwtService.generarPreAuthToken(
                    u.getCliente().getId(), correo, sesionProps.getPreAuthTokenMinutos());
            log.info("Login etapa 1 OK (2FA requerido) correo={}", correo);
            return LoginResponse.requiere2FA(preAuth, u.getCliente().getId(), correo);
        }

        // Sin 2FA: creamos sesion y emitimos token real.
        return emitirAccessToken(u, forceLogin, userAgent, ipAddress);
    }

    // ===================================================================
    // Etapa 2: preAuthToken + codigo TOTP -> access token real
    // ===================================================================
    @Override
    @Transactional
    public LoginResponse verificar2FA(Totp2FAVerificarRequest req, boolean forceLogin,
                                      String userAgent, String ipAddress) {
        Claims claims;
        try {
            claims = jwtService.validarPreAuthToken(req.preAuthToken());
        } catch (JwtException ex) {
            log.warn("PreAuthToken invalido: {}", ex.getMessage());
            throw new PreAuthTokenInvalidoException();
        }
        String correo = claims.getSubject();
        Usuario u = usuarioRepository.findByCorreo(correo)
                .orElseThrow(CredencialesInvalidasException::new);

        if (!Boolean.TRUE.equals(u.getActivo()) || !Boolean.TRUE.equals(u.getCliente().getActivo())) {
            throw new UsuarioInactivoException();
        }

        lockoutService.verificarNoBloqueado(correo);
        if (!totpService.validarCodigo(correo, req.codigo())) {
            lockoutService.registrarFallo(correo);
            throw new TotpCodigoInvalidoException();
        }
        lockoutService.registrarExito(correo);

        return emitirAccessToken(u, forceLogin, userAgent, ipAddress);
    }

    // ===================================================================
    // Helper: crea la sesion server-side (puede 409) y firma el JWT real
    // ===================================================================
    private LoginResponse emitirAccessToken(Usuario u, boolean forceLogin,
                                            String userAgent, String ipAddress) {
        String jti = UUID.randomUUID().toString();
        SesionActiva sesion = sesionService.crearSesion(
                u.getCliente().getId(), jti, userAgent, ipAddress, forceLogin);
        String token = jwtService.generarToken(u.getCliente().getId(), u.getCorreo(), sesion.getJti());
        return LoginResponse.accesoDirecto(
                token, jwtService.getExpirationSeconds(), u.getCliente().getId(), u.getCorreo());
    }
}
