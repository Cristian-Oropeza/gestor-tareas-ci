package com.proyecto.servicios.onboarding.exception;

import org.springframework.http.HttpStatus;

public class SesionRevocadaException extends BusinessException {
    public SesionRevocadaException() { super("La sesion fue cerrada o expiro por inactividad"); }
    public SesionRevocadaException(String mensaje) { super(mensaje); }
    @Override public HttpStatus getStatus() { return HttpStatus.UNAUTHORIZED; }
    @Override public String getCodigo()     { return "SESION_REVOCADA"; }
}
