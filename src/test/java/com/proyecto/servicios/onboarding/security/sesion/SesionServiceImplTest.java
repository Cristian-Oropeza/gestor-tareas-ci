package com.proyecto.servicios.onboarding.security.sesion;

import com.proyecto.servicios.onboarding.entity.SesionActiva;
import com.proyecto.servicios.onboarding.exception.SesionDuplicadaException;
import com.proyecto.servicios.onboarding.exception.SesionNoEncontradaException;
import com.proyecto.servicios.onboarding.repository.SesionActivaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SesionServiceImplTest {

    @Mock SesionActivaRepository repo;
    Clock clock;
    SesionServiceImpl service;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-11-24T20:00:00Z"), ZoneOffset.UTC);
        service = new SesionServiceImpl(repo, clock);
    }

    @Test
    void crearSesion_sinSesionesPrevias_creaOk() {
        when(repo.findByClienteIdAndActivaTrueOrderByFechaCreacionDesc(1L)).thenReturn(List.of());
        when(repo.save(any(SesionActiva.class))).thenAnswer(inv -> {
            SesionActiva s = inv.getArgument(0);
            s.setId(99L);
            return s;
        });

        SesionActiva nueva = service.crearSesion(
                1L, "jti-nuevo", "Mozilla/5.0 Windows Chrome/120.0.0.0", "189.1.1.1", false);

        assertThat(nueva.getId()).isEqualTo(99L);
        assertThat(nueva.getJti()).isEqualTo("jti-nuevo");
        assertThat(nueva.getDispositivo()).contains("chrome").contains("windows");
        assertThat(nueva.getActiva()).isTrue();
        verify(repo, never()).cerrarPorCliente(any(), any(), any());
    }

    @Test
    void crearSesion_conSesionPrevia_sinForce_lanza409ConDetalles() {
        SesionActiva previa = SesionActiva.builder()
                .id(5L).clienteId(1L).jti("jti-viejo")
                .dispositivo("movil-android")
                .fechaCreacion(OffsetDateTime.now(clock).minusHours(2))
                .ultimaActividad(OffsetDateTime.now(clock).minusMinutes(5))
                .activa(Boolean.TRUE).build();
        when(repo.findByClienteIdAndActivaTrueOrderByFechaCreacionDesc(1L)).thenReturn(List.of(previa));

        assertThatThrownBy(() -> service.crearSesion(1L, "jti-new", "ua", "ip", false))
                .isInstanceOfSatisfying(SesionDuplicadaException.class, ex -> {
                    assertThat(ex.getSesionesActivas()).hasSize(1);
                    assertThat(ex.getSesionesActivas().get(0).dispositivo()).isEqualTo("movil-android");
                });
        verify(repo, never()).save(any());
    }

    @Test
    void crearSesion_conSesionPrevia_conForce_cierraPreviasYCrea() {
        SesionActiva previa = SesionActiva.builder()
                .id(5L).clienteId(1L).jti("viejo").activa(Boolean.TRUE)
                .fechaCreacion(OffsetDateTime.now(clock).minusHours(1))
                .ultimaActividad(OffsetDateTime.now(clock).minusMinutes(1))
                .build();
        when(repo.findByClienteIdAndActivaTrueOrderByFechaCreacionDesc(1L)).thenReturn(List.of(previa));
        when(repo.cerrarPorCliente(eq(1L), eq("FORZADO_POR_NUEVO_LOGIN"), any())).thenReturn(1);
        when(repo.save(any(SesionActiva.class))).thenAnswer(inv -> inv.getArgument(0));

        SesionActiva nueva = service.crearSesion(1L, "jti-forced", "ua", "ip", true);

        assertThat(nueva.getJti()).isEqualTo("jti-forced");
        verify(repo).cerrarPorCliente(eq(1L), eq("FORZADO_POR_NUEVO_LOGIN"), any(OffsetDateTime.class));
    }

    @Test
    void cerrarPorJti_marcaInactiva() {
        SesionActiva s = SesionActiva.builder()
                .id(7L).clienteId(1L).jti("abc").activa(Boolean.TRUE).build();
        when(repo.findByJtiAndActivaTrue("abc")).thenReturn(Optional.of(s));

        service.cerrarPorJti("abc");

        ArgumentCaptor<SesionActiva> cap = ArgumentCaptor.forClass(SesionActiva.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getActiva()).isFalse();
        assertThat(cap.getValue().getMotivoCierre()).isEqualTo("LOGOUT");
    }

    @Test
    void cerrarPorJti_noExiste_noHaceNada() {
        when(repo.findByJtiAndActivaTrue("x")).thenReturn(Optional.empty());
        service.cerrarPorJti("x");
        verify(repo, never()).save(any());
    }

    @Test
    void cerrarPorId_deOtroCliente_lanzaNoEncontrada() {
        SesionActiva s = SesionActiva.builder().id(10L).clienteId(999L).activa(Boolean.TRUE).build();
        when(repo.findById(10L)).thenReturn(Optional.of(s));

        assertThatThrownBy(() -> service.cerrarPorId(10L, 1L))
                .isInstanceOf(SesionNoEncontradaException.class);
        verify(repo, never()).save(any());
    }

    @Test
    void estaActiva_delegaAlRepositorio() {
        when(repo.findByJtiAndActivaTrue("x")).thenReturn(Optional.of(new SesionActiva()));
        assertThat(service.estaActiva("x")).isTrue();
        when(repo.findByJtiAndActivaTrue("y")).thenReturn(Optional.empty());
        assertThat(service.estaActiva("y")).isFalse();
    }

    @Test
    void listarActivas_marcaCorrectamenteLaSesionActual() {
        SesionActiva a = SesionActiva.builder().id(1L).clienteId(1L).jti("actual").activa(Boolean.TRUE)
                .fechaCreacion(OffsetDateTime.now(clock)).ultimaActividad(OffsetDateTime.now(clock)).build();
        SesionActiva b = SesionActiva.builder().id(2L).clienteId(1L).jti("otra").activa(Boolean.TRUE)
                .fechaCreacion(OffsetDateTime.now(clock)).ultimaActividad(OffsetDateTime.now(clock)).build();
        when(repo.findByClienteIdAndActivaTrueOrderByFechaCreacionDesc(1L)).thenReturn(List.of(a, b));

        var lista = service.listarActivas(1L, "actual");
        assertThat(lista).hasSize(2);
        assertThat(lista.get(0).esSesionActual()).isTrue();
        assertThat(lista.get(1).esSesionActual()).isFalse();
    }
}
