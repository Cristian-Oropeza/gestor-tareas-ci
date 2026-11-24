package com.proyecto.servicios.onboarding.security.totp;

import com.proyecto.servicios.onboarding.dto.response.Totp2FASetupResponse;

public interface TotpService {

    /**
     * Inicia el setup 2FA para el usuario dado: genera (o regenera si no estaba
     * activado) un secret TOTP, lo cifra y persiste, genera N scratch codes
     * hasheados y devuelve el QR mas los scratch codes en claro (unica vez).
     * Idempotente mientras activado=false; falla si ya esta activado.
     */
    Totp2FASetupResponse iniciarSetup(String correo);

    /**
     * Activa el 2FA validando que el usuario haya escaneado bien el QR y lea
     * un codigo valido de su app. Pasa de activado=false a activado=true.
     */
    void activar(String correo, String codigo);

    /**
     * Desactiva 2FA tras re-validar la contrasenia del usuario. Borra el secret
     * y los scratch codes para evitar fuga residual.
     */
    void desactivar(String correo, String password);

    /**
     * Valida un codigo TOTP (o un scratch code) para el usuario. Usado por el
     * flujo /auth/2fa/verificar (Tanda 4). Implementa lockout y anti-replay.
     * @return true si el codigo es valido.
     */
    boolean validarCodigo(String correo, String codigo);

    /** True si el usuario tiene 2FA configurado Y activado. No lanza si no existe row. */
    boolean tieneActivo(String correo);
}
