package com.proyecto.servicios.onboarding.service;

import com.proyecto.servicios.onboarding.dto.request.PaginacionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

class PaginacionFactoryTest {

    private final PaginacionFactory factory = new PaginacionFactory();

    @Test
    void toPageable_reqNull_devuelveDefault() {
        Pageable p = factory.toPageable(null);
        assertThat(p.getPageNumber()).isEqualTo(0);
        assertThat(p.getPageSize()).isEqualTo(20);
    }

    @Test
    void toPageable_sortAsc() {
        PaginacionRequest r = new PaginacionRequest();
        r.setSort("nombre,asc");
        Pageable p = factory.toPageable(r);
        Sort.Order o = p.getSort().getOrderFor("nombre");
        assertThat(o).isNotNull();
        assertThat(o.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void toPageable_sortDesc() {
        PaginacionRequest r = new PaginacionRequest();
        r.setSort("id,desc");
        Pageable p = factory.toPageable(r);
        assertThat(p.getSort().getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void toPageable_sortBlank_usaDefault() {
        PaginacionRequest r = new PaginacionRequest();
        r.setSort("");
        Pageable p = factory.toPageable(r);
        assertThat(p.getSort().getOrderFor("fechaCreacion").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void toPageable_sortNull_usaDefault() {
        PaginacionRequest r = new PaginacionRequest();
        r.setSort(null);
        Pageable p = factory.toPageable(r);
        assertThat(p.getSort().getOrderFor("fechaCreacion")).isNotNull();
    }

    @Test
    void toPageable_sortSinDireccion_usaDesc() {
        PaginacionRequest r = new PaginacionRequest();
        r.setSort("correo");
        Pageable p = factory.toPageable(r);
        assertThat(p.getSort().getOrderFor("correo").getDirection()).isEqualTo(Sort.Direction.DESC);
    }
}
