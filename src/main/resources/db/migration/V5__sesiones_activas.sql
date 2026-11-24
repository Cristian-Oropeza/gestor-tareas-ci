-- ============================================================================
-- Fase 2A: Sesion unica server-side
-- ============================================================================
-- Cada JWT emitido lleva un jti (JWT id, UUID) que se inserta aqui como
-- unica fuente de verdad. El JwtAuthenticationFilter valida en CADA request
-- que el jti siga presente con activa=true. Un logout o un "force-login"
-- marca activa=false y el JWT queda revocado inmediatamente, aunque no haya
-- expirado por fecha.
-- ============================================================================

CREATE TABLE sesiones_activas (
    id                  BIGSERIAL      PRIMARY KEY,
    cliente_id          BIGINT         NOT NULL,
    jti                 VARCHAR(64)    NOT NULL,
    dispositivo         VARCHAR(100),
    user_agent          VARCHAR(500),
    ip_address          VARCHAR(50),
    activa              BOOLEAN        NOT NULL DEFAULT TRUE,
    motivo_cierre       VARCHAR(40),
    fecha_creacion      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    ultima_actividad    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    fecha_cierre        TIMESTAMPTZ,

    CONSTRAINT uk_sesiones_jti              UNIQUE (jti),
    CONSTRAINT fk_sesiones_cliente          FOREIGN KEY (cliente_id) REFERENCES clientes(id),
    CONSTRAINT ck_sesiones_motivo_cierre    CHECK (motivo_cierre IN
        ('LOGOUT','FORZADO_POR_NUEVO_LOGIN','EXPIRADA_POR_INACTIVIDAD','CERRADA_POR_USUARIO'))
);

CREATE INDEX idx_sesiones_cliente_activa ON sesiones_activas (cliente_id) WHERE activa = TRUE;
CREATE INDEX idx_sesiones_jti_activa     ON sesiones_activas (jti)        WHERE activa = TRUE;

COMMENT ON TABLE  sesiones_activas                    IS 'Fase 2A: sesion unica server-side';
COMMENT ON COLUMN sesiones_activas.jti                IS 'JWT id unico; lo lleva cada token emitido';
COMMENT ON COLUMN sesiones_activas.dispositivo        IS 'Etiqueta derivada del User-Agent: web-chrome-windows / movil-flutter-android';
