package com.proyecto.servicios.onboarding.service.impl;

import java.util.Collections;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import com.proyecto.servicios.onboarding.dto.request.CuentaConsultaRequest;
import com.proyecto.servicios.onboarding.dto.request.CuentaCreateRequest;
import com.proyecto.servicios.onboarding.dto.request.CuentaUpdateRequest;
import com.proyecto.servicios.onboarding.exception.CuentaNoEncontradaException;
import com.proyecto.servicios.onboarding.dto.response.CuentaResponse;
import com.proyecto.servicios.onboarding.entity.Cliente;
import com.proyecto.servicios.onboarding.entity.Cuenta;
import com.proyecto.servicios.onboarding.entity.enums.EstatusCuenta;
import com.proyecto.servicios.onboarding.exception.ClienteNoEncontradoException;
import com.proyecto.servicios.onboarding.exception.ValidacionNegocioException;
import com.proyecto.servicios.onboarding.mapper.ClienteMapper;
import com.proyecto.servicios.onboarding.repository.ClienteRepository;
import com.proyecto.servicios.onboarding.repository.CuentaRepository;
import com.proyecto.servicios.onboarding.service.NumeroCuentaGenerator;
import com.proyecto.servicios.onboarding.service.PaginacionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class CuentaServiceImplTest {

    @Mock CuentaRepository cuentaRepository;
    @Mock ClienteRepository clienteRepository;
    @Mock NumeroCuentaGenerator generator;
    @Mock ClienteMapper mapper;
    @Mock PaginacionFactory paginacionFactory;
    @InjectMocks CuentaServiceImpl service;

    private Cliente cliente;

    @BeforeEach
    void setup() {
        cliente = Cliente.builder().id(1L).activo(Boolean.TRUE).build();
        ReflectionTestUtils.setField(service, "saldoDefault", new BigDecimal("0.00"));
    }

    @Test
    void crear_exitosoConSaldoCustom() {
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(generator.generar()).thenReturn("014180111111111111");
        when(cuentaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.aCuentaResponse(any())).thenReturn(
                new CuentaResponse(1L, "014180111111111111", 1L,
                        new BigDecimal("2000.00"), EstatusCuenta.ACTIVA, null));

        CuentaResponse r = service.crear(new CuentaCreateRequest(1L, new BigDecimal("2000.00")));

        assertThat(r.numeroCuenta()).isEqualTo("014180111111111111");
    }

    @Test
    void crear_lanzaCuandoClienteNoExiste() {
        when(clienteRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.crear(new CuentaCreateRequest(99L, null)))
                .isInstanceOf(ClienteNoEncontradoException.class);
    }

    @Test
    void crear_lanzaCuandoClienteInactivo() {
        cliente.setActivo(Boolean.FALSE);
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        assertThatThrownBy(() -> service.crear(new CuentaCreateRequest(1L, null)))
                .isInstanceOf(ValidacionNegocioException.class);
    }

    @Test
    void crear_lanzaCuandoSaldoNegativo() {
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        assertThatThrownBy(() -> service.crear(new CuentaCreateRequest(1L, new BigDecimal("-10.00"))))
                .isInstanceOf(ValidacionNegocioException.class);
    }

    @Test
    void consultar_pagina() {
        Cuenta ct = Cuenta.builder().id(1L).numeroCuenta("014180111111111111")
                .cliente(cliente).saldo(new BigDecimal("100.00")).estatus(EstatusCuenta.ACTIVA).build();
        PageRequest pr = PageRequest.of(0, 10);
        when(paginacionFactory.toPageable(any())).thenReturn(pr);
        when(cuentaRepository.findAll(any(Specification.class), eq(pr)))
                .thenReturn(new PageImpl<>(List.of(ct)));
        when(mapper.aCuentaResponse(ct)).thenReturn(
                new CuentaResponse(1L, "014180111111111111", 1L, new BigDecimal("100.00"), EstatusCuenta.ACTIVA, null));

        Page<CuentaResponse> r = service.consultar(new CuentaConsultaRequest());
        assertThat(r.getTotalElements()).isEqualTo(1);
    }

    @Test
    void actualizarParcial_cambiaEstatus() {
        Cuenta ct = Cuenta.builder().id(1L).numeroCuenta("014180111111111111")
                .cliente(cliente).saldo(new BigDecimal("100.00")).estatus(EstatusCuenta.ACTIVA).build();
        when(cuentaRepository.findByNumeroCuenta("014180111111111111")).thenReturn(Optional.of(ct));
        when(cuentaRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(mapper.aCuentaResponse(any())).thenReturn(
                new CuentaResponse(1L, "014180111111111111", 1L, new BigDecimal("100.00"), EstatusCuenta.BLOQUEADA, null));

        CuentaUpdateRequest req = new CuentaUpdateRequest();
        req.setNumeroCuenta("014180111111111111");
        req.setEstatus(EstatusCuenta.BLOQUEADA);

        CuentaResponse r = service.actualizarParcial(req);

        assertThat(ct.getEstatus()).isEqualTo(EstatusCuenta.BLOQUEADA);
        assertThat(r.estatus()).isEqualTo(EstatusCuenta.BLOQUEADA);
    }

    @Test
    void actualizarParcial_lanzaCuandoCuentaNoExiste() {
        when(cuentaRepository.findByNumeroCuenta("014180999999999999")).thenReturn(Optional.empty());
        CuentaUpdateRequest req = new CuentaUpdateRequest();
        req.setNumeroCuenta("014180999999999999");
        req.setEstatus(EstatusCuenta.ACTIVA);

        assertThatThrownBy(() -> service.actualizarParcial(req))
                .isInstanceOf(CuentaNoEncontradaException.class);
    }

    @Test
    void actualizarParcial_lanzaCuandoActivaConClienteInactivo() {
        cliente.setActivo(Boolean.FALSE);
        Cuenta ct = Cuenta.builder().id(1L).numeroCuenta("014180111111111111")
                .cliente(cliente).saldo(new BigDecimal("100.00")).estatus(EstatusCuenta.INACTIVA).build();
        when(cuentaRepository.findByNumeroCuenta("014180111111111111")).thenReturn(Optional.of(ct));

        CuentaUpdateRequest req = new CuentaUpdateRequest();
        req.setNumeroCuenta("014180111111111111");
        req.setEstatus(EstatusCuenta.ACTIVA);

        assertThatThrownBy(() -> service.actualizarParcial(req))
                .isInstanceOf(ValidacionNegocioException.class);
    }


    @Test
    void consultar_todosLosFiltros_ejecutaSpecCompleta() {
        CuentaConsultaRequest req = new CuentaConsultaRequest();
        req.setNumeroCuenta("014180111111111111");
        req.setClienteId(1L);
        req.setEstatus(EstatusCuenta.ACTIVA);
        PageRequest pr = PageRequest.of(0, 10);
        when(paginacionFactory.toPageable(any())).thenReturn(pr);
        when(cuentaRepository.findAll(any(Specification.class), eq(pr))).thenAnswer(inv -> {
            Specification<Cuenta> spec = inv.getArgument(0);
            Root<Cuenta> root = mock(Root.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
            CriteriaQuery<?> query = mock(CriteriaQuery.class);
            CriteriaBuilder cb = mock(CriteriaBuilder.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
            spec.toPredicate(root, query, cb);
            return new PageImpl<>(Collections.emptyList());
        });
        service.consultar(req);
    }


    @Test
    void consultar_conNumeroBlank_cubreBranchFalse() {
        CuentaConsultaRequest req = new CuentaConsultaRequest();
        req.setNumeroCuenta(""); // blank
        PageRequest pr = PageRequest.of(0, 10);
        when(paginacionFactory.toPageable(any())).thenReturn(pr);
        when(cuentaRepository.findAll(any(Specification.class), eq(pr))).thenAnswer(inv -> {
            Specification<Cuenta> spec = inv.getArgument(0);
            Root<Cuenta> root = mock(Root.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
            CriteriaQuery<?> query = mock(CriteriaQuery.class);
            CriteriaBuilder cb = mock(CriteriaBuilder.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
            spec.toPredicate(root, query, cb);
            return new PageImpl<>(Collections.emptyList());
        });
        service.consultar(req);
    }

}
