package com.proyecto.servicios.onboarding.exception;

import org.springframework.http.HttpStatus;

public class SesionNoEncontradaException extends BusinessException {
    public SesionNoEncontradaException() { super("La sesion indicada no existe o no te pertenece"); }
    public SesionNoEncontradaException(String mensaje) { super(mensaje); }
    @Override public HttpStatus getStatus() { return HttpStatus.NOT_FOUND; }
    @Override public String getCodigo()     { return "SESION_NO_ENCONTRADA"; }
}
