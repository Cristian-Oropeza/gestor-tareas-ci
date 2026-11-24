package com.proyecto.servicios.onboarding.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuracion global de OpenAPI/Swagger: info del proyecto + esquema de
 * seguridad "bearerAuth". El esquema aparece como candado global en Swagger UI
 * y los endpoints que ponen @SecurityRequirement(name = "bearerAuth") se
 * marcan explicitamente como protegidos.
 */
@Configuration
public class OpenApiOnboardingConfig {

    @Bean
    public OpenAPI onboardingOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PWA_Prueba")
                        .version("1.0.0")
                        .description(
                            "API REST del ecosistema de comercio GestoPago.\n\n" +
                            "Incluye onboarding de clientes, transacciones GestoPago, 2FA (TOTP), " +
                            "sesion unica server-side, login en 2 etapas, rate limiting, lockout " +
                            "de cuenta, catalogo sincronizado e historial."))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT emitido por /api/v1/auth/login (sin 2FA) o /api/v1/auth/2fa/verificar (con 2FA)")));
    }
}
