package com.proyecto.servicios.onboarding.exception;

import org.springframework.http.HttpStatus;

/** 429 Too Many Requests: se excedio el limite de peticiones por IP. */
public class RateLimitExcedidoException extends BusinessException {
    public RateLimitExcedidoException() {
        super("Demasiados intentos. Espera unos minutos antes de volver a intentar.");
    }
    @Override public HttpStatus getStatus() { return HttpStatus.TOO_MANY_REQUESTS; }
    @Override public String getCodigo()     { return "RATE_LIMIT_EXCEDIDO"; }
}
