package com.proyecto.servicios.onboarding.service;

import com.proyecto.servicios.onboarding.dto.request.LoginRequest;
import com.proyecto.servicios.onboarding.dto.request.Totp2FAVerificarRequest;
import com.proyecto.servicios.onboarding.dto.response.LoginResponse;

public interface AuthService {
    /**
     * Etapa 1 del login. Valida password; si el usuario NO tiene 2FA, crea sesion
     * y emite token real. Si SI tiene 2FA, devuelve preAuthToken sin crear sesion.
     * La sesion unica (409) se evalua hasta que haya access token real.
     */
    LoginResponse login(LoginRequest req, boolean forceLogin, String userAgent, String ipAddress);

    /**
     * Etapa 2 del login: intercambia preAuthToken + codigo TOTP por el access token
     * real. Aqui se aplica el control de sesion unica (puede lanzar 409).
     */
    LoginResponse verificar2FA(Totp2FAVerificarRequest req, boolean forceLogin,
                               String userAgent, String ipAddress);
}
