-- ============================================================================
-- Fase 2A: 2FA (TOTP Google Authenticator)
-- ============================================================================
-- Guarda el estado de 2FA por usuario. El secret TOTP queda cifrado con AES
-- (llave en env TOTP_SECRET_ENCRYPTION_KEY). Los scratch codes se guardan
-- hasheados con BCrypt, separados por comas, y se van invalidando al usarse.
-- Lockout: tras N intentos fallidos consecutivos, se bloquea por X minutos.
-- ============================================================================

CREATE TABLE usuario_totp (
    usuario_id          BIGINT         PRIMARY KEY,
    secret_cifrado      VARCHAR(500)   NOT NULL,
    activado            BOOLEAN        NOT NULL DEFAULT FALSE,
    scratch_codes       TEXT,
    intentos_fallidos   SMALLINT       NOT NULL DEFAULT 0,
    bloqueado_hasta     TIMESTAMPTZ,
    ultimo_codigo_usado VARCHAR(10),
    fecha_ultimo_uso    TIMESTAMPTZ,
    fecha_activacion    TIMESTAMPTZ,
    fecha_creacion      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    fecha_actualizacion TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_usuario_totp_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuarios(id) ON DELETE CASCADE,
    CONSTRAINT ck_usuario_totp_intentos CHECK (intentos_fallidos >= 0)
);

CREATE INDEX idx_usuario_totp_activado ON usuario_totp (activado) WHERE activado = TRUE;

COMMENT ON TABLE  usuario_totp                       IS 'Estado 2FA por usuario (Fase 2A)';
COMMENT ON COLUMN usuario_totp.secret_cifrado        IS 'Secret base32 cifrado con AES-GCM. Llave viene de env.';
COMMENT ON COLUMN usuario_totp.scratch_codes         IS 'Codigos de respaldo hash BCrypt separados por coma. Marcados "USED:" al consumirse.';
COMMENT ON COLUMN usuario_totp.ultimo_codigo_usado   IS 'Ultimo codigo TOTP aceptado. Previene replay dentro del mismo paso de 30s.';
