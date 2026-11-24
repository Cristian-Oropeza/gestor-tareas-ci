package com.proyecto.servicios.onboarding.security.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configuracion del lockout de cuenta. Mapea app.lockout.* */
@Component
@ConfigurationProperties(prefix = "app.lockout")
@Getter @Setter
public class LockoutProperties {
    private boolean enabled = true;
    /** Intentos fallidos consecutivos por correo antes de bloquear. */
    private int maxIntentos = 5;
    /** Duracion del bloqueo de cuenta (minutos). */
    private int duracionMinutos = 15;
}
