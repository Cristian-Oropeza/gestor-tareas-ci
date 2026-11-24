package com.proyecto.servicios.onboarding.controller;

import com.proyecto.servicios.onboarding.dto.request.LoginRequest;
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
@Tag(name = "Auth", description = "Autenticacion y emision de JWT")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Login por correo y contrasenia; retorna JWT. 409 si ya hay sesion activa.")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest req,
            @RequestHeader(value = "X-Force-Login", required = false, defaultValue = "false") boolean forceLogin,
            HttpServletRequest http) {
        String ua = http.getHeader("User-Agent");
        String ip = extraerIp(http);
        return ResponseEntity.ok(authService.login(req, forceLogin, ua, ip));
    }

    private String extraerIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            // Puede venir como "client, proxy1, proxy2"; tomamos el primero.
            int coma = xff.indexOf(',');
            return (coma > 0 ? xff.substring(0, coma) : xff).trim();
        }
        return req.getRemoteAddr();
    }
}
