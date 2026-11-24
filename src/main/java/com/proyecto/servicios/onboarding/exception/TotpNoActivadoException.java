package com.proyecto.servicios.onboarding.exception;

import org.springframework.http.HttpStatus;

public class TotpNoActivadoException extends BusinessException {
    public TotpNoActivadoException() { super("El 2FA esta configurado pero aun no activado"); }
    public TotpNoActivadoException(String mensaje) { super(mensaje); }
    @Override public HttpStatus getStatus() { return HttpStatus.BAD_REQUEST; }
    @Override public String getCodigo()     { return "TOTP_NO_ACTIVADO"; }
}
