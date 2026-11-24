package com.proyecto.servicios.onboarding.exception;

import org.springframework.http.HttpStatus;

public class TotpNoConfiguradoException extends BusinessException {
    public TotpNoConfiguradoException() { super("El 2FA no esta configurado para este usuario"); }
    public TotpNoConfiguradoException(String mensaje) { super(mensaje); }
    @Override public HttpStatus getStatus() { return HttpStatus.BAD_REQUEST; }
    @Override public String getCodigo()     { return "TOTP_NO_CONFIGURADO"; }
}
