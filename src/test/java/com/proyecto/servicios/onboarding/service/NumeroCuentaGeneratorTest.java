package com.proyecto.servicios.onboarding.service;

import com.proyecto.servicios.onboarding.repository.CuentaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NumeroCuentaGeneratorTest {

    @Mock CuentaRepository repo;
    @InjectMocks NumeroCuentaGenerator gen;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(gen, "prefijo", "014180");
    }

    @Test
    void generar_devuelveNumeroDe18DigitosConPrefijo() {
        when(repo.existsByNumeroCuenta(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        String r = gen.generar();
        assertThat(r).hasSize(18).startsWith("014180").matches("\\d{18}");
    }

    @Test
    void generar_reintentaSiColisiona() {
        when(repo.existsByNumeroCuenta(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(true, true, false);
        String r = gen.generar();
        assertThat(r).hasSize(18);
    }

    @Test
    void generar_lanzaExcepcionSiTodosLosIntentosColisionan() {
        when(repo.existsByNumeroCuenta(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);
        assertThatThrownBy(() -> gen.generar())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No se pudo generar");
    }
}
