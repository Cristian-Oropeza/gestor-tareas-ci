package com.proyecto.servicios.onboarding.security.sesion;

import com.proyecto.servicios.onboarding.dto.response.SesionActivaResponse;
import com.proyecto.servicios.onboarding.entity.SesionActiva;

import java.util.List;

public interface SesionService {

    SesionActiva crearSesion(Long clienteId, String jti, String userAgent, String ipAddress, boolean forceLogin);

    void cerrarPorJti(String jti);

    List<SesionActivaResponse> listarActivas(Long clienteId, String jtiActual);

    void cerrarPorId(Long sesionId, Long clienteId);

    boolean estaActiva(String jti);

    void tocarActividad(String jti);
}
