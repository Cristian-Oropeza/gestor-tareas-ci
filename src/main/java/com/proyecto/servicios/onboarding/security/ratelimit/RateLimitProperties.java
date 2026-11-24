package com.proyecto.servicios.onboarding.security.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configuracion de rate limiting por IP (Bucket4j). Mapea app.rate-limit.* */
@Component
@ConfigurationProperties(prefix = "app.rate-limit")
@Getter @Setter
public class RateLimitProperties {
    private boolean enabled = true;
    /** Intentos de login/verificar2FA por IP en ventana rolling de 15 min. */
    private int loginPor15Min = 5;
    /** Limite general por IP para mitigar DoS basico. */
    private int ipPorMinuto = 60;
}
