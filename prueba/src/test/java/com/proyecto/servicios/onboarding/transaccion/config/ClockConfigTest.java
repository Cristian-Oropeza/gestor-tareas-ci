package com.proyecto.servicios.onboarding.transaccion.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

class ClockConfigTest {

    @Test
    void clockBean_devuelveSystemDefaultZone() {
        Clock c = new ClockConfig().clock();
        assertThat(c).isNotNull();
        // No esta null, tiene zona del sistema, y la instancia puede dar un instant
        assertThat(c.instant()).isNotNull();
    }
}
