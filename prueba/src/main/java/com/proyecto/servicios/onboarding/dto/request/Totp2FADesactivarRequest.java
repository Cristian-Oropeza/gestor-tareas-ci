package com.proyecto.servicios.onboarding.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** Para desactivar 2FA se exige la contrasenia actual (re-auth defensivo). */
public record Totp2FADesactivarRequest(
        @Schema(description = "Contrasenia actual del usuario", example = "MiPass123!")
        @NotBlank(message = "La contrasenia es obligatoria")
        String password
) {}
