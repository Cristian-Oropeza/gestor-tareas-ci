package com.proyecto.servicios.onboarding.transaccion.dto.request;

import com.proyecto.servicios.onboarding.dto.request.PaginacionRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Filtros opcionales para consultar el catalogo. Todos los filtros son
 * opcionales; un request vacio devuelve todos los productos activos paginados.
 */
@Getter @Setter @NoArgsConstructor
public class CatalogoConsultarRequest {

    @Schema(description = "Filtra por id_servicio exacto (ej: TELCEL)", example = "TELCEL")
    @Size(max = 50)
    private String idServicio;

    @Schema(description = "Busqueda parcial en descripcion (case-insensitive)", example = "Telcel")
    @Size(max = 100)
    private String descripcion;

    @Schema(description = "Si true, incluye productos inactivos (default: false)")
    private Boolean incluirInactivos = Boolean.FALSE;

    @Valid
    @Schema(description = "Paginacion estandar")
    private PaginacionRequest paginacion = new PaginacionRequest();
}
