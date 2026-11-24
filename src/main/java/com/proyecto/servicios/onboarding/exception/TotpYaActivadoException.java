package com.proyecto.servicios.onboarding.exception;

import org.springframework.http.HttpStatus;

public class TotpYaActivadoException extends BusinessException {
    public TotpYaActivadoException() { super("El 2FA ya esta activado para este usuario"); }
    public TotpYaActivadoException(String mensaje) { super(mensaje); }
    @Override public HttpStatus getStatus() { return HttpStatus.CONFLICT; }
    @Override public String getCodigo()     { return "TOTP_YA_ACTIVADO"; }
}
