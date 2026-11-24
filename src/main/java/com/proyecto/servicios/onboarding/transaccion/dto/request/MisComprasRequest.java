package com.proyecto.servicios.onboarding.transaccion.dto.request;

import com.proyecto.servicios.onboarding.dto.request.PaginacionRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Filtros opcionales del historial de compras. El clienteId SIEMPRE se toma
 * del JWT en el controller: cualquier valor que venga en el body se ignora.
 */
@Getter @Setter
public class MisComprasRequest {

    @Schema(description = "Filtra desde esta fecha (inclusive). Opcional.")
    private OffsetDateTime desde;

    @Schema(description = "Filtra hasta esta fecha (exclusive). Opcional.")
    private OffsetDateTime hasta;

    @Valid
    @Schema(description = "Paginacion estandar (default: pagina 0, size 20, sort fechaConfirmacion desc)")
    private PaginacionRequest paginacion;

    public MisComprasRequest() {
        this.paginacion = new PaginacionRequest();
        this.paginacion.setSort("fechaConfirmacion,desc");
    }
}
