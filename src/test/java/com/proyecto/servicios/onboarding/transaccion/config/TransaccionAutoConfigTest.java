package com.proyecto.servicios.onboarding.transaccion.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TransaccionAutoConfigTest {

    @Test
    void autoConfig_puedeInstanciarse() {
        TransaccionAutoConfig cfg = new TransaccionAutoConfig();
        assertThat(cfg).isNotNull();
    }
}
