package com.proyecto.servicios.onboarding.security.ratelimit;

import com.proyecto.servicios.onboarding.entity.Cliente;
import com.proyecto.servicios.onboarding.entity.Usuario;
import com.proyecto.servicios.onboarding.exception.CuentaBloqueadaException;
import com.proyecto.servicios.onboarding.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LockoutServiceImplTest {

    @Mock UsuarioRepository usuarioRepository;
    Clock clock;
    LockoutProperties props;
    LockoutServiceImpl service;

    Usuario usuario;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-11-24T20:00:00Z"), ZoneOffset.UTC);
        props = new LockoutProperties();
        props.setEnabled(true);
        props.setMaxIntentos(5);
        props.setDuracionMinutos(15);
        service = new LockoutServiceImpl(usuarioRepository, props, clock);
        usuario = Usuario.builder()
                .id(1L).correo("x@y.com").password("hash").activo(Boolean.TRUE)
                .cliente(Cliente.builder().id(1L).activo(Boolean.TRUE).build())
                .intentosFallidos((short) 0).build();
    }

    @Test
    void verificarNoBloqueado_sinBloqueo_noLanza() {
        when(usuarioRepository.findByCorreo("x@y.com")).thenReturn(Optional.of(usuario));
        service.verificarNoBloqueado("x@y.com");  // no exception
    }

    @Test
    void verificarNoBloqueado_bloqueoVigente_lanza() {
        usuario.setBloqueadoHasta(OffsetDateTime.now(clock).plusMinutes(5));
        when(usuarioRepository.findByCorreo("x@y.com")).thenReturn(Optional.of(usuario));
        assertThatThrownBy(() -> service.verificarNoBloqueado("x@y.com"))
                .isInstanceOf(CuentaBloqueadaException.class);
    }

    @Test
    void verificarNoBloqueado_bloqueoYaVencido_noLanza() {
        usuario.setBloqueadoHasta(OffsetDateTime.now(clock).minusMinutes(1));
        when(usuarioRepository.findByCorreo("x@y.com")).thenReturn(Optional.of(usuario));
        service.verificarNoBloqueado("x@y.com");  // no exception
    }

    @Test
    void registrarFallo_antesDeUmbral_soloIncrementa() {
        usuario.setIntentosFallidos((short) 2);
        when(usuarioRepository.findByCorreo("x@y.com")).thenReturn(Optional.of(usuario));

        service.registrarFallo("x@y.com");

        assertThat(usuario.getIntentosFallidos()).isEqualTo((short) 3);
        assertThat(usuario.getBloqueadoHasta()).isNull();
    }

    @Test
    void registrarFallo_alLlegarAlUmbral_bloqueaYResetea() {
        usuario.setIntentosFallidos((short) 4);  // proximo fallo llega a 5
        when(usuarioRepository.findByCorreo("x@y.com")).thenReturn(Optional.of(usuario));

        service.registrarFallo("x@y.com");

        assertThat(usuario.getBloqueadoHasta()).isNotNull();
        assertThat(usuario.getBloqueadoHasta()).isAfter(OffsetDateTime.now(clock));
        assertThat(usuario.getIntentosFallidos()).isEqualTo((short) 0);
    }

    @Test
    void registrarExito_limpiaContadorYBloqueo() {
        usuario.setIntentosFallidos((short) 3);
        usuario.setBloqueadoHasta(OffsetDateTime.now(clock).minusMinutes(1));
        when(usuarioRepository.findByCorreo("x@y.com")).thenReturn(Optional.of(usuario));

        service.registrarExito("x@y.com");

        assertThat(usuario.getIntentosFallidos()).isEqualTo((short) 0);
        assertThat(usuario.getBloqueadoHasta()).isNull();
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void disabled_esNoOp() {
        props.setEnabled(false);
        service.registrarFallo("x@y.com");
        service.verificarNoBloqueado("x@y.com");
        verifyNoInteractions(usuarioRepository);
    }
}
