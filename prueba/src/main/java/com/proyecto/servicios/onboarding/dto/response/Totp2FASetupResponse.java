package com.proyecto.servicios.onboarding.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Respuesta del setup 2FA. El QR y los scratch codes se devuelven UNA SOLA VEZ,
 * el usuario debe guardarlos en ese momento. En llamadas posteriores el backend
 * NUNCA los vuelve a entregar.
 */
public record Totp2FASetupResponse(
        @Schema(description = "Issuer visible en la app autenticadora", example = "GestoPago Comercio PWA Oropeza")
        String issuer,

        @Schema(description = "Label de la entrada (usualmente el correo)", example = "usuario@correo.com")
        String label,

        @Schema(description = "URI otpauth:// completa, por si el front prefiere renderizar el QR el mismo")
        String otpauthUri,

        @Schema(description = "QR como data URI base64 PNG listo para pintar en un <img src=\"...\">")
        String qrImageDataUri,

        @Schema(description = "Scratch codes de respaldo (texto plano, UNA SOLA VEZ). Guardalos en papel o password manager.")
        List<String> scratchCodes
) {}
