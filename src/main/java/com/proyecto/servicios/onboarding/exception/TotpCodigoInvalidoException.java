package com.proyecto.servicios.onboarding.exception;

import org.springframework.http.HttpStatus;

public class TotpCodigoInvalidoException extends BusinessException {
    public TotpCodigoInvalidoException() { super("Codigo 2FA invalido"); }
    public TotpCodigoInvalidoException(String mensaje) { super(mensaje); }
    @Override public HttpStatus getStatus() { return HttpStatus.UNAUTHORIZED; }
    @Override public String getCodigo()     { return "TOTP_CODIGO_INVALIDO"; }
}
