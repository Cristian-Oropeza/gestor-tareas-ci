package com.proyecto.servicios.onboarding.service.impl;

import com.proyecto.servicios.onboarding.dto.request.LoginRequest;
import com.proyecto.servicios.onboarding.dto.response.LoginResponse;
import com.proyecto.servicios.onboarding.entity.SesionActiva;
import com.proyecto.servicios.onboarding.entity.Usuario;
import com.proyecto.servicios.onboarding.exception.CredencialesInvalidasException;
import com.proyecto.servicios.onboarding.exception.UsuarioInactivoException;
import com.proyecto.servicios.onboarding.repository.UsuarioRepository;
import com.proyecto.servicios.onboarding.security.JwtService;
import com.proyecto.servicios.onboarding.security.sesion.SesionService;
import com.proyecto.servicios.onboarding.service.AuthService;
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

    @Override
    @Transactional
    public LoginResponse login(LoginRequest req, boolean forceLogin, String userAgent, String ipAddress) {
        String correo = req.correo().toLowerCase().trim();
        Usuario u = usuarioRepository.findByCorreo(correo)
                .orElseThrow(CredencialesInvalidasException::new);

        if (!Boolean.TRUE.equals(u.getActivo()) || !Boolean.TRUE.equals(u.getCliente().getActivo())) {
            throw new UsuarioInactivoException();
        }
        if (!passwordEncoder.matches(req.password(), u.getPassword())) {
            log.warn("Login fallido para correo={}", correo);
            throw new CredencialesInvalidasException();
        }

        // Primero creo la sesion (puede lanzar 409 si ya hay una activa y no se forzo).
        String jti = UUID.randomUUID().toString();
        SesionActiva sesion = sesionService.crearSesion(
                u.getCliente().getId(), jti, userAgent, ipAddress, forceLogin);

        // Emito el JWT ligado a ese jti.
        String token = jwtService.generarToken(u.getCliente().getId(), u.getCorreo(), sesion.getJti());
        return new LoginResponse(
                token, "Bearer",
                jwtService.getExpirationSeconds(),
                u.getCliente().getId(), u.getCorreo()
        );
    }
}
