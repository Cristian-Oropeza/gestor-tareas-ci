package com.proyecto.servicios.onboarding.transaccion.service.impl;

import com.proyecto.servicios.onboarding.transaccion.entity.CatalogoProducto;
import com.proyecto.servicios.onboarding.transaccion.exception.ProductoNoEncontradoException;
import com.proyecto.servicios.onboarding.transaccion.repository.CatalogoProductoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogoProductoServiceImplTest {

    @Mock CatalogoProductoRepository repo;
    @InjectMocks CatalogoProductoServiceImpl service;

    @Test
    void obtenerActivoOExcepcion_devuelveProductoCuandoExisteYActivo() {
        CatalogoProducto p = CatalogoProducto.builder()
                .idServicio("TELCEL").idProducto("TELCEL_100")
                .activo(true).montoMin(new BigDecimal("100.00")).montoMax(new BigDecimal("100.00"))
                .build();
        when(repo.findByIdServicioAndIdProducto("TELCEL", "TELCEL_100")).thenReturn(Optional.of(p));

        CatalogoProducto resultado = service.obtenerActivoOExcepcion("TELCEL", "TELCEL_100");

        assertThat(resultado).isSameAs(p);
    }

    @Test
    void obtenerActivoOExcepcion_lanzaCuandoNoExiste() {
        when(repo.findByIdServicioAndIdProducto("X", "Y")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenerActivoOExcepcion("X", "Y"))
                .isInstanceOf(ProductoNoEncontradoException.class);
    }

    @Test
    void obtenerActivoOExcepcion_lanzaCuandoProductoInactivo() {
        CatalogoProducto inactivo = CatalogoProducto.builder()
                .idServicio("TELCEL").idProducto("TELCEL_100").activo(false)
                .montoMin(new BigDecimal("1.00")).montoMax(new BigDecimal("1.00"))
                .build();
        when(repo.findByIdServicioAndIdProducto("TELCEL", "TELCEL_100")).thenReturn(Optional.of(inactivo));

        assertThatThrownBy(() -> service.obtenerActivoOExcepcion("TELCEL", "TELCEL_100"))
                .isInstanceOf(ProductoNoEncontradoException.class);
    }
}
