package com.proyecto.servicios.onboarding.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenApiOnboardingConfigTest {

    @Test
    void onboardingOpenAPI_tieneInfoYSecurityScheme() {
        OpenAPI api = new OpenApiOnboardingConfig().onboardingOpenAPI();
        assertThat(api).isNotNull();
        assertThat(api.getInfo()).isNotNull();
        assertThat(api.getInfo().getTitle()).isEqualTo("PWA_Prueba");
        assertThat(api.getInfo().getVersion()).isEqualTo("1.0.0");
        assertThat(api.getSecurity()).hasSize(1);
        assertThat(api.getComponents().getSecuritySchemes()).containsKey("bearerAuth");
    }
}
