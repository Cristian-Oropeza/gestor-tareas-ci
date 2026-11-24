package com.proyecto.servicios.onboarding.exception;

import org.springframework.http.HttpStatus;

/** 423 Locked: la cuenta esta bloqueada temporalmente por intentos fallidos. */
public class CuentaBloqueadaException extends BusinessException {
    public CuentaBloqueadaException() {
        super("La cuenta esta bloqueada temporalmente por intentos fallidos");
    }
    public CuentaBloqueadaException(String msg) { super(msg); }
    @Override public HttpStatus getStatus() { return HttpStatus.LOCKED; }
    @Override public String getCodigo()     { return "CUENTA_BLOQUEADA"; }
}
