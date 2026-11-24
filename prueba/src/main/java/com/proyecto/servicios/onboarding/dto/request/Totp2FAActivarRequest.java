package com.proyecto.servicios.onboarding.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Codigo TOTP de 6 digitos que confirma que el usuario escaneo bien el QR. */
public record Totp2FAActivarRequest(
        @Schema(description = "Codigo TOTP de 6 digitos de la app autenticadora", example = "123456")
        @NotBlank(message = "El codigo es obligatorio")
        @Pattern(regexp = "^[0-9]{6}$", message = "El codigo debe tener exactamente 6 digitos")
        String codigo
) {}
