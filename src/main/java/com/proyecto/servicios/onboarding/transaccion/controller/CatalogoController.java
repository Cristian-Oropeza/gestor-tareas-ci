package com.proyecto.servicios.onboarding.transaccion.controller;

import com.proyecto.servicios.onboarding.dto.response.GenericResponse;
import com.proyecto.servicios.onboarding.transaccion.dto.request.CatalogoConsultarRequest;
import com.proyecto.servicios.onboarding.transaccion.dto.response.CatalogoProductoResponse;
import com.proyecto.servicios.onboarding.transaccion.service.CatalogoConsultaService;
import com.proyecto.servicios.onboarding.transaccion.service.CatalogoSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catalogo de productos GestoPago. Lectura siempre va contra la BD local (que el
 * scheduler mantiene al dia); sincronizar/ consulta GestoPago en vivo (admin).
 */
@RestController
@RequestMapping("/api/v1/catalogo")
@RequiredArgsConstructor
@Tag(name = "Catalogo", description = "Catalogo de productos sincronizado desde GestoPago")
@SecurityRequirement(name = "bearerAuth")
public class CatalogoController {

    private final CatalogoConsultaService consulta;
    private final CatalogoSyncService sync;

    @PostMapping("/consultar")
    @Operation(summary = "Lista productos del catalogo con filtros opcionales y paginacion")
    public ResponseEntity<Page<CatalogoProductoResponse>> consultar(
            @Valid @RequestBody(required = false) CatalogoConsultarRequest req) {
        if (req == null) req = new CatalogoConsultarRequest();
        return ResponseEntity.ok(consulta.consultar(req));
    }

    @PostMapping("/sincronizar")
    @Operation(summary = "Fuerza una sincronizacion con GestoPago (uso admin)")
    public ResponseEntity<GenericResponse> sincronizar() {
        int n = sync.sincronizar();
        return ResponseEntity.ok(GenericResponse.builder()
                .codigo(200)
                .mensaje("Sincronizacion ejecutada: " + n + " productos procesados")
                .build());
    }
}
