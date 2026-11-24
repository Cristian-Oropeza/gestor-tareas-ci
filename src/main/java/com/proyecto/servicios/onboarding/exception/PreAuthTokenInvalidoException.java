package com.proyecto.servicios.onboarding.exception;

import org.springframework.http.HttpStatus;

public class PreAuthTokenInvalidoException extends BusinessException {
    public PreAuthTokenInvalidoException() {
        super("El token de verificacion 2FA es invalido o expiro");
    }
    @Override public HttpStatus getStatus() { return HttpStatus.UNAUTHORIZED; }
    @Override public String getCodigo()     { return "PRE_AUTH_TOKEN_INVALIDO"; }
}
