package com.proyecto.servicios.onboarding.transaccion.service.impl;

import com.proyecto.servicios.onboarding.dto.request.PaginacionRequest;
import com.proyecto.servicios.onboarding.transaccion.dto.request.CatalogoConsultarRequest;
import com.proyecto.servicios.onboarding.transaccion.entity.CatalogoProducto;
import com.proyecto.servicios.onboarding.transaccion.repository.CatalogoProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogoConsultaServiceImplTest {

    @Mock CatalogoProductoRepository repo;
    CatalogoConsultaServiceImpl service;

    @BeforeEach
    void setUp() { service = new CatalogoConsultaServiceImpl(repo); }

    @Test
    void consultar_paginaMapea_aDto() {
        CatalogoProducto p = CatalogoProducto.builder()
                .id(1L).idServicio("TELCEL").idProducto("TELCEL_100")
                .descripcion("Recarga Telcel 100").tipoFront((short)1)
                .precio(new BigDecimal("100.00"))
                .montoMin(new BigDecimal("100.00")).montoMax(new BigDecimal("100.00"))
                .moneda("MXN").activo(Boolean.TRUE).build();
        when(repo.findAll(any(Specification.class), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(p)));

        CatalogoConsultarRequest req = new CatalogoConsultarRequest();
        req.setPaginacion(new PaginacionRequest());
        Page<?> pageResult = service.consultar(req);

        assertThat(pageResult.getContent()).hasSize(1);
    }
}
