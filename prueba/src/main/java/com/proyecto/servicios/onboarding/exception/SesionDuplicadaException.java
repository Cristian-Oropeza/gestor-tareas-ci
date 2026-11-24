package com.proyecto.servicios.onboarding.exception;

import com.proyecto.servicios.onboarding.dto.response.SesionActivaResponse;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * 409: existe una sesion activa del mismo cliente. El payload incluye los
 * detalles de la(s) sesion(es) activa(s) para que el frontend ofrezca
 * "Cerrar la otra sesion" (reintenta con header X-Force-Login: true).
 */
@Getter
public class SesionDuplicadaException extends BusinessException {

    private final List<SesionActivaResponse> sesionesActivas;

    public SesionDuplicadaException(List<SesionActivaResponse> sesionesActivas) {
        super("Existe una sesion activa en otro dispositivo");
        this.sesionesActivas = sesionesActivas;
    }

    @Override public HttpStatus getStatus() { return HttpStatus.CONFLICT; }
    @Override public String getCodigo()     { return "SESION_DUPLICADA"; }
}
