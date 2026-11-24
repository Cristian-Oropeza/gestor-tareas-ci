package com.proyecto.servicios.onboarding.controller;

import com.proyecto.servicios.onboarding.dto.request.LoginRequest;
import com.proyecto.servicios.onboarding.dto.request.Totp2FAVerificarRequest;
import com.proyecto.servicios.onboarding.dto.response.LoginResponse;
import com.proyecto.servicios.onboarding.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Login con 2 etapas (password + 2FA opcional) y JWT")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Etapa 1: correo+password. Devuelve token o preAuthToken si requiere 2FA. 409 si hay sesion activa.")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest req,
            @RequestHeader(value = "X-Force-Login", required = false, defaultValue = "false") boolean forceLogin,
            HttpServletRequest http) {
        return ResponseEntity.ok(
                authService.login(req, forceLogin, http.getHeader("User-Agent"), extraerIp(http)));
    }

    @PostMapping("/2fa/verificar")
    @Operation(summary = "Etapa 2: intercambia preAuthToken + codigo TOTP por el access token real.")
    public ResponseEntity<LoginResponse> verificar2FA(
            @Valid @RequestBody Totp2FAVerificarRequest req,
            @RequestHeader(value = "X-Force-Login", required = false, defaultValue = "false") boolean forceLogin,
            HttpServletRequest http) {
        return ResponseEntity.ok(
                authService.verificar2FA(req, forceLogin, http.getHeader("User-Agent"), extraerIp(http)));
    }

    private String extraerIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int coma = xff.indexOf(',');
            return (coma > 0 ? xff.substring(0, coma) : xff).trim();
        }
        return req.getRemoteAddr();
    }
}
