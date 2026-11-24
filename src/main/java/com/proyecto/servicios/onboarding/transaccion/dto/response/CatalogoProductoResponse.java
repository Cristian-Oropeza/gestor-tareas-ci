package com.proyecto.servicios.onboarding.transaccion.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CatalogoProductoResponse(
        @Schema(example = "1")        Long id,
        @Schema(example = "TELCEL")   String idServicio,
        @Schema(example = "TELCEL_100") String idProducto,
        @Schema(example = "Recarga Telcel $100") String descripcion,
        @Schema(description = "1=recarga precio fijo, 2=pago con referencia", example = "1")
        Short tipoFront,
        @Schema(example = "100.00")   BigDecimal precio,
        @Schema(example = "20.00")    BigDecimal montoMin,
        @Schema(example = "500.00")   BigDecimal montoMax,
        @Schema(example = "MXN")      String moneda,
        @Schema(example = "true")     Boolean activo,
        @Schema(description = "URL opcional de imagen") String imagenUrl,
        @Schema(description = "Ultima sincronizacion con GestoPago") OffsetDateTime fechaSincronizacion
) {}
