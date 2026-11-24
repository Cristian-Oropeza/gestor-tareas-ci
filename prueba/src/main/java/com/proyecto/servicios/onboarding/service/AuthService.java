package com.proyecto.servicios.onboarding.service;

import com.proyecto.servicios.onboarding.dto.request.LoginRequest;
import com.proyecto.servicios.onboarding.dto.response.LoginResponse;

public interface AuthService {
    /**
     * Login con password + control de sesion unica. Si ya existe sesion activa
     * del mismo cliente:
     *   - forceLogin=true  -> cierra la anterior y crea nueva
     *   - forceLogin=false -> lanza SesionDuplicadaException (409)
     */
    LoginResponse login(LoginRequest req, boolean forceLogin, String userAgent, String ipAddress);
}
