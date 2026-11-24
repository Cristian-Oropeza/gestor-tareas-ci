package com.proyecto.servicios.onboarding.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SesionCerrarRequest(
        @Schema(description = "Id de la sesion a cerrar", example = "42")
        @NotNull(message = "sesionId es obligatorio")
        @Positive(message = "sesionId debe ser positivo")
        Long sesionId
) {}
