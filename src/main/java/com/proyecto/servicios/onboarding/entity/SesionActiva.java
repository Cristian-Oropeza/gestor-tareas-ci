package com.proyecto.servicios.onboarding.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * Sesion activa de un cliente. Un JWT emitido -> un row aqui. Mientras activa=true
 * el JWT es valido; al pasar activa=false (logout, force-login, inactividad) el
 * JWT queda revocado sin importar su expiracion.
 */
@Entity
@Table(name = "sesiones_activas")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class SesionActiva {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cliente_id", nullable = false)
    private Long clienteId;

    @Column(nullable = false, length = 64, unique = true)
    private String jti;

    @Column(length = 100)
    private String dispositivo;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activa = Boolean.TRUE;

    /** LOGOUT, FORZADO_POR_NUEVO_LOGIN, EXPIRADA_POR_INACTIVIDAD, CERRADA_POR_USUARIO */
    @Column(name = "motivo_cierre", length = 40)
    private String motivoCierre;

    @Column(name = "fecha_creacion", nullable = false)
    private OffsetDateTime fechaCreacion;

    @Column(name = "ultima_actividad", nullable = false)
    private OffsetDateTime ultimaActividad;

    @Column(name = "fecha_cierre")
    private OffsetDateTime fechaCierre;
}
