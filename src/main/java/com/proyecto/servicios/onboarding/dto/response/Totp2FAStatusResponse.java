package com.proyecto.servicios.onboarding.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Estado del 2FA del usuario autenticado (lo consulta el UI al cargar). */
public record Totp2FAStatusResponse(
        @Schema(description = "True si el usuario tiene 2FA activo y confirmado", example = "true")
        boolean activo
) {}
