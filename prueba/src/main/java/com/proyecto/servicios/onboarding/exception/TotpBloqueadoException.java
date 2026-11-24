package com.proyecto.servicios.onboarding.exception;

import org.springframework.http.HttpStatus;

public class TotpBloqueadoException extends BusinessException {
    public TotpBloqueadoException() { super("2FA bloqueado temporalmente por intentos fallidos"); }
    public TotpBloqueadoException(String mensaje) { super(mensaje); }
    @Override public HttpStatus getStatus() { return HttpStatus.LOCKED; }
    @Override public String getCodigo()     { return "TOTP_BLOQUEADO"; }
}
