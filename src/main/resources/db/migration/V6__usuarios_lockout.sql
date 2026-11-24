-- ============================================================================
-- Fase 2A: Account lockout por intentos fallidos
-- ============================================================================
-- Agrega contador de intentos consecutivos y marca temporal de bloqueo a la
-- tabla usuarios. El LockoutService lee/escribe estas columnas en el flujo de
-- /auth/login y /auth/2fa/verificar.
-- ============================================================================

ALTER TABLE usuarios
    ADD COLUMN intentos_fallidos SMALLINT    NOT NULL DEFAULT 0,
    ADD COLUMN bloqueado_hasta   TIMESTAMPTZ;

ALTER TABLE usuarios
    ADD CONSTRAINT ck_usuarios_intentos CHECK (intentos_fallidos >= 0);

CREATE INDEX idx_usuarios_bloqueado ON usuarios (bloqueado_hasta)
    WHERE bloqueado_hasta IS NOT NULL;

COMMENT ON COLUMN usuarios.intentos_fallidos IS 'Contador de intentos fallidos consecutivos (reset a 0 en login exitoso)';
COMMENT ON COLUMN usuarios.bloqueado_hasta   IS 'Fecha/hora hasta la que la cuenta esta bloqueada. NULL si no esta bloqueada.';
