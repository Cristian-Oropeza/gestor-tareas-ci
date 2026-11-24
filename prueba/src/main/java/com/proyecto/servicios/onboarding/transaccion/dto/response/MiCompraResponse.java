package com.proyecto.servicios.onboarding.transaccion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Item del historial. Solo devuelve datos "limpios" del usuario; no expone
 * campos internos como payload cifrado, intentos de conciliacion, etc.
 */
public record MiCompraResponse(
        @Schema(example = "42") Long id,
        @Schema(example = "op-2026-11-24-abc") String referenciaOperacion,
        @Schema(example = "TELCEL") String idServicio,
        @Schema(example = "TELCEL_100") String idProducto,
        @Schema(example = "5512345678") String telefono,
        @Schema(example = "100.00") BigDecimal monto,
        @Schema(example = "MXN") String moneda,
        @Schema(description = "Numero de autorizacion del proveedor (positivo si aplicada)")
        Long numAutorizacion,
        @Schema(description = "Fecha de confirmacion (APLICADA)") OffsetDateTime fechaConfirmacion
) {}
