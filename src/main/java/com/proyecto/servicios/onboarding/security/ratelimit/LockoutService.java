package com.proyecto.servicios.onboarding.security.ratelimit;

public interface LockoutService {
    /** Lanza CuentaBloqueadaException si el usuario tiene bloqueado_hasta > ahora. */
    void verificarNoBloqueado(String correo);

    /** Suma 1 a intentos_fallidos. Si llega al umbral, setea bloqueado_hasta y resetea el contador al desbloquear. */
    void registrarFallo(String correo);

    /** Resetea intentos_fallidos y limpia bloqueado_hasta en un login exitoso. */
    void registrarExito(String correo);
}
