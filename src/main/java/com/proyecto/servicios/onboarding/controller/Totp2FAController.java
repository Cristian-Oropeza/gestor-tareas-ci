package com.proyecto.servicios.onboarding.controller;

import com.proyecto.servicios.onboarding.dto.request.Totp2FAActivarRequest;
import com.proyecto.servicios.onboarding.dto.request.Totp2FADesactivarRequest;
import com.proyecto.servicios.onboarding.dto.response.GenericResponse;
import com.proyecto.servicios.onboarding.dto.response.Totp2FASetupResponse;
import com.proyecto.servicios.onboarding.exception.CredencialesInvalidasException;
import com.proyecto.servicios.onboarding.security.totp.TotpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints de setup y administracion del segundo factor (2FA TOTP).
 *
 * Los tres endpoints requieren usuario autenticado (JWT ya emitido).
 * La verificacion del codigo en el flujo de LOGIN vive en AuthController
 * bajo /auth/2fa/verificar (Tanda 4) porque usa un pre-auth token, no un JWT.
 */
@RestController
@RequestMapping("/api/v1/auth/2fa")
@RequiredArgsConstructor
@Tag(name = "2FA", description = "Setup y administracion del segundo factor (TOTP)")
@SecurityRequirement(name = "bearerAuth")
public class Totp2FAController {

    private final TotpService totpService;

    @PostMapping("/setup")
    @Operation(summary = "Inicia el setup 2FA: genera secret, QR y scratch codes (UNA SOLA VEZ)")
    public ResponseEntity<Totp2FASetupResponse> setup(Authentication auth) {
        String correo = requireCorreo(auth);
        return ResponseEntity.ok(totpService.iniciarSetup(correo));
    }

    @PostMapping("/activar")
    @Operation(summary = "Activa 2FA validando que el usuario haya escaneado bien el QR")
    public ResponseEntity<GenericResponse> activar(
            Authentication auth,
            @Valid @RequestBody Totp2FAActivarRequest req) {
        String correo = requireCorreo(auth);
        totpService.activar(correo, req.codigo());
        return ResponseEntity.ok(
                GenericResponse.builder().codigo(200).mensaje("2FA activado correctamente").build());
    }

    @PostMapping("/desactivar")
    @Operation(summary = "Desactiva 2FA tras re-validar la contrasenia del usuario")
    public ResponseEntity<GenericResponse> desactivar(
            Authentication auth,
            @Valid @RequestBody Totp2FADesactivarRequest req) {
        String correo = requireCorreo(auth);
        totpService.desactivar(correo, req.password());
        return ResponseEntity.ok(
                GenericResponse.builder().codigo(200).mensaje("2FA desactivado").build());
    }

    private String requireCorreo(Authentication auth) {
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            throw new CredencialesInvalidasException();
        }
        return auth.getName();
    }
}
