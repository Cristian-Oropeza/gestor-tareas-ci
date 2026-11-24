package com.proyecto.servicios.onboarding.security.sesion;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Configuracion de sesion unica. Mapea app.sesion.* del YAML. */
@Component
@ConfigurationProperties(prefix = "app.sesion")
@Getter @Setter
public class SesionProperties {
    private int inactividadMinutos = 30;
    private String cleanupCron = "0 */5 * * * *";
    private int preAuthTokenMinutos = 5;
}
