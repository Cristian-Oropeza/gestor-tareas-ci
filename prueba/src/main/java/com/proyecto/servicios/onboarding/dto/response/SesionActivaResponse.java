package com.proyecto.servicios.onboarding.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

public record SesionActivaResponse(
        @Schema(description = "Id interno de la sesion", example = "42")
        Long sesionId,

        @Schema(description = "Etiqueta de dispositivo derivada del User-Agent",
                example = "web-chrome-windows")
        String dispositivo,

        @Schema(description = "IP desde la que se creo la sesion", example = "189.133.xxx.xx")
        String ipAddress,

        @Schema(description = "Fecha en que se abrio la sesion")
        OffsetDateTime fechaCreacion,

        @Schema(description = "Ultima actividad registrada")
        OffsetDateTime ultimaActividad,

        @Schema(description = "True si corresponde a la sesion desde la que se hizo la consulta")
        boolean esSesionActual
) {}
