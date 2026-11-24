package com.proyecto.servicios.onboarding.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/**
 * Estado 2FA (TOTP) por usuario. Se crea al llamar /auth/2fa/setup, se marca
 * activado=true al completar /auth/2fa/activar, y se valida el codigo contra
 * el secret en cada /auth/2fa/verificar.
 *
 * El secret TOTP SIEMPRE esta cifrado en la columna secret_cifrado; nunca se
 * expone en respuestas HTTP despues del setup inicial.
 */
@Entity
@Table(name = "usuario_totp")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class UsuarioTotp {

    /** PK = FK a usuarios(id). Relacion OneToOne compartiendo id. */
    @Id
    @Column(name = "usuario_id")
    private Long usuarioId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "usuario_id", foreignKey = @ForeignKey(name = "fk_usuario_totp_usuario"))
    private Usuario usuario;

    /** Secret base32 cifrado con AES-GCM. Nunca se persiste en claro. */
    @Column(name = "secret_cifrado", nullable = false, length = 500)
    private String secretCifrado;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activado = Boolean.FALSE;

    /** Scratch codes hash BCrypt separados por coma. Prefijo "USED:" cuando se consumen. */
    @Column(name = "scratch_codes", columnDefinition = "TEXT")
    private String scratchCodes;

    @Column(name = "intentos_fallidos", nullable = false)
    @Builder.Default
    private Short intentosFallidos = 0;

    @Column(name = "bloqueado_hasta")
    private OffsetDateTime bloqueadoHasta;

    /** Ultimo codigo TOTP aceptado; previene replay dentro del mismo paso de 30s. */
    @Column(name = "ultimo_codigo_usado", length = 10)
    private String ultimoCodigoUsado;

    @Column(name = "fecha_ultimo_uso")
    private OffsetDateTime fechaUltimoUso;

    @Column(name = "fecha_activacion")
    private OffsetDateTime fechaActivacion;

    @CreationTimestamp
    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private OffsetDateTime fechaCreacion;

    @UpdateTimestamp
    @Column(name = "fecha_actualizacion", nullable = false)
    private OffsetDateTime fechaActualizacion;
}
