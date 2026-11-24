package com.proyecto.servicios.onboarding.service.impl;

import com.proyecto.servicios.onboarding.dto.request.LoginRequest;
import com.proyecto.servicios.onboarding.dto.response.LoginResponse;
import com.proyecto.servicios.onboarding.entity.Cliente;
import com.proyecto.servicios.onboarding.entity.Usuario;
import com.proyecto.servicios.onboarding.exception.CredencialesInvalidasException;
import com.proyecto.servicios.onboarding.exception.UsuarioInactivoException;
import com.proyecto.servicios.onboarding.repository.UsuarioRepository;
import com.proyecto.servicios.onboarding.security.JwtService;
import com.proyecto.servicios.onboarding.security.sesion.SesionService;
import com.proyecto.servicios.onboarding.entity.SesionActiva;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock UsuarioRepository usuarioRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock SesionService sesionService;
    @InjectMocks AuthServiceImpl service;

    private Cliente cliente;
    private Usuario usuario;

    @BeforeEach
    void setup() {
        cliente = Cliente.builder().id(1L).activo(Boolean.TRUE).build();
        usuario = Usuario.builder()
                .id(10L).correo("test@demo.mx").password("$2a$10$encoded").activo(Boolean.TRUE)
                .cliente(cliente).build();
    }

    @Test
    void login_exitoso_devuelveToken() {
        when(usuarioRepository.findByCorreo("test@demo.mx")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Secreto1!", usuario.getPassword())).thenReturn(true);
        when(sesionService.crearSesion(anyLong(), anyString(), any(), any(), anyBoolean()))
                .thenReturn(SesionActiva.builder().id(1L).jti("jti-test").build());
        when(jwtService.generarToken(1L, "test@demo.mx", "jti-test")).thenReturn("tok-abc");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);

        LoginResponse r = service.login(new LoginRequest("Test@Demo.MX", "Secreto1!"), false, "ua-test", "127.0.0.1");

        assertThat(r.token()).isEqualTo("tok-abc");
        assertThat(r.tipo()).isEqualTo("Bearer");
        assertThat(r.expiraEn()).isEqualTo(3600L);
        assertThat(r.clienteId()).isEqualTo(1L);
    }

    @Test
    void login_usuarioNoExiste_lanzaCredenciales() {
        when(usuarioRepository.findByCorreo(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.login(new LoginRequest("nope@demo.mx", "x"), false, "ua-test", "127.0.0.1"))
                .isInstanceOf(CredencialesInvalidasException.class);
    }

    @Test
    void login_usuarioInactivo_lanza() {
        usuario.setActivo(Boolean.FALSE);
        when(usuarioRepository.findByCorreo("test@demo.mx")).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> service.login(new LoginRequest("test@demo.mx", "x"), false, "ua-test", "127.0.0.1"))
                .isInstanceOf(UsuarioInactivoException.class);
    }

    @Test
    void login_clienteInactivo_lanza() {
        cliente.setActivo(Boolean.FALSE);
        when(usuarioRepository.findByCorreo("test@demo.mx")).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> service.login(new LoginRequest("test@demo.mx", "x"), false, "ua-test", "127.0.0.1"))
                .isInstanceOf(UsuarioInactivoException.class);
    }

    @Test
    void login_passwordIncorrecta_lanza() {
        when(usuarioRepository.findByCorreo("test@demo.mx")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("test@demo.mx", "mala"), false, "ua-test", "127.0.0.1"))
                .isInstanceOf(CredencialesInvalidasException.class);
    }
}
