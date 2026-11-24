package com.proyecto.servicios.onboarding.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Etapa 2 del login: intercambiar el pre-auth token + codigo TOTP por un access
 * token real. Acepta tanto un codigo numerico de 6 digitos como un scratch code
 * de 10 caracteres alfanumericos del setup inicial.
 */
public record Totp2FAVerificarRequest(
        @Schema(description = "preAuthToken devuelto por /auth/login (vigencia 5 min)")
        @NotBlank(message = "preAuthToken es obligatorio")
        String preAuthToken,

        @Schema(description = "Codigo TOTP de 6 digitos de la app, o scratch code de 10 chars",
                example = "123456")
        @NotBlank(message = "El codigo es obligatorio")
        @Pattern(regexp = "^([0-9]{6}|[A-Z2-9]{10})$",
                 message = "Debe ser un codigo de 6 digitos o un scratch code de 10 caracteres")
        @Size(min = 6, max = 10)
        String codigo
) {}
