package com.proyecto.servicios.onboarding.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Respuesta de login. Dos formas posibles:
 *  - Usuario SIN 2FA: {token, tipo, expiraEn, clienteId, correo, requiere2FA=false}
 *  - Usuario CON 2FA: {requiere2FA=true, preAuthToken, clienteId, correo} y el frontend
 *                     debe ir a /auth/2fa/verificar para obtener el token real.
 *
 * Los campos null (token/tipo/expiraEn en etapa 1, preAuthToken en etapa final)
 * NO se serializan gracias a @JsonInclude(NON_NULL).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(
        @Schema(description = "JWT real con jti activo en sesion server-side. Null si requiere2FA=true.")
        String token,

        @Schema(description = "Tipo del token (siempre 'Bearer' cuando token != null)", example = "Bearer")
        String tipo,

        @Schema(description = "Segundos de vigencia del token. Null si requiere2FA=true.")
        Long expiraEn,

        @Schema(description = "Id del cliente autenticado")
        Long clienteId,

        @Schema(description = "Correo del usuario autenticado")
        String correo,

        @Schema(description = "True cuando falta el segundo factor (hay que ir a /auth/2fa/verificar).")
        Boolean requiere2FA,

        @Schema(description = "JWT corto (5 min) para completar el 2FA. Solo se envia cuando requiere2FA=true.")
        String preAuthToken
) {
    /** Constructor conveniente para login sin 2FA (compat con Tanda 3). */
    public static LoginResponse accesoDirecto(String token, long expiraEn, Long clienteId, String correo) {
        return new LoginResponse(token, "Bearer", expiraEn, clienteId, correo, Boolean.FALSE, null);
    }

    /** Constructor conveniente para login etapa 1 (pide 2FA). */
    public static LoginResponse requiere2FA(String preAuthToken, Long clienteId, String correo) {
        return new LoginResponse(null, null, null, clienteId, correo, Boolean.TRUE, preAuthToken);
    }
}
