package com.proyecto.servicios.onboarding.controller;

import com.proyecto.servicios.onboarding.dto.request.SesionCerrarRequest;
import com.proyecto.servicios.onboarding.dto.response.GenericResponse;
import com.proyecto.servicios.onboarding.dto.response.SesionActivaResponse;
import com.proyecto.servicios.onboarding.exception.CredencialesInvalidasException;
import com.proyecto.servicios.onboarding.security.JwtService;
import com.proyecto.servicios.onboarding.security.sesion.SesionService;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints de administracion de sesion: logout, listar sesiones activas,
 * cerrar una sesion especifica. Todos requieren JWT valido.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Sesiones", description = "Logout y administracion de sesiones activas")
@SecurityRequirement(name = "bearerAuth")
public class SesionController {

    private final SesionService sesionService;
    private final JwtService jwtService;

    @PostMapping("/logout")
    @Operation(summary = "Cierra la sesion actual (revoca el JWT por jti)")
    public ResponseEntity<GenericResponse> logout(HttpServletRequest req) {
        String jti = extraerJtiDelRequest(req);
        sesionService.cerrarPorJti(jti);
        return ResponseEntity.ok(GenericResponse.builder()
                .codigo(200).mensaje("Sesion cerrada correctamente").build());
    }

    @PostMapping("/sesiones/activas")
    @Operation(summary = "Lista las sesiones activas del usuario autenticado")
    public ResponseEntity<List<SesionActivaResponse>> listarActivas(HttpServletRequest req) {
        Long clienteId = extraerClienteId(req);
        String jti = extraerJtiDelRequest(req);
        return ResponseEntity.ok(sesionService.listarActivas(clienteId, jti));
    }

    @PostMapping("/sesiones/cerrar")
    @Operation(summary = "Cierra una sesion especifica por id (solo las propias)")
    public ResponseEntity<GenericResponse> cerrarSesion(HttpServletRequest req,
                                                        @Valid @RequestBody SesionCerrarRequest body) {
        Long clienteId = extraerClienteId(req);
        sesionService.cerrarPorId(body.sesionId(), clienteId);
        return ResponseEntity.ok(GenericResponse.builder()
                .codigo(200).mensaje("Sesion cerrada").build());
    }

    // ===== helpers =====

    private String extraerJtiDelRequest(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) throw new CredencialesInvalidasException();
        Claims claims = jwtService.validar(auth.substring(7).trim());
        String jti = claims.getId();
        if (jti == null || jti.isBlank()) throw new CredencialesInvalidasException();
        return jti;
    }

    private Long extraerClienteId(HttpServletRequest req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) throw new CredencialesInvalidasException();
        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) throw new CredencialesInvalidasException();
        Claims claims = jwtService.validar(header.substring(7).trim());
        Number n = claims.get("clienteId", Number.class);
        if (n == null) throw new CredencialesInvalidasException();
        return n.longValue();
    }
}
