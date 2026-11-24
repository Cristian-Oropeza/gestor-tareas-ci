package com.proyecto.servicios.onboarding.service.impl;

import com.proyecto.servicios.onboarding.dto.request.LoginRequest;
import com.proyecto.servicios.onboarding.dto.request.Totp2FAVerificarRequest;
import com.proyecto.servicios.onboarding.exception.PreAuthTokenInvalidoException;
import com.proyecto.servicios.onboarding.exception.TotpCodigoInvalidoException;
import com.proyecto.servicios.onboarding.entity.SesionActiva;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.mockito.Mockito;

import com.proyecto.servicios.onboarding.dto.response.LoginResponse;
import com.proyecto.servicios.onboarding.entity.Cliente;
import com.proyecto.servicios.onboarding.entity.Usuario;
import com.proyecto.servicios.onboarding.exception.CredencialesInvalidasException;
import com.proyecto.servicios.onboarding.exception.UsuarioInactivoException;
import com.proyecto.servicios.onboarding.repository.UsuarioRepository;
import com.proyecto.servicios.onboarding.security.JwtService;
import com.proyecto.servicios.onboarding.security.sesion.SesionService;
import com.proyecto.servicios.onboarding.security.sesion.SesionProperties;
import com.proyecto.servicios.onboarding.security.totp.TotpService;
import com.proyecto.servicios.onboarding.security.ratelimit.LockoutService;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock UsuarioRepository usuarioRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock SesionService sesionService;
    @Mock TotpService totpService;
    @Mock LockoutService lockoutService;
    @Mock SesionProperties sesionProps;
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
    // ====================== Tanda 4: flujo 2 etapas ======================

    @Test
    void login_usuarioCon2FA_devuelvePreAuthTokenSinCrearSesion() {
        when(usuarioRepository.findByCorreo("test@demo.mx")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("ok", usuario.getPassword())).thenReturn(true);
        when(totpService.tieneActivo("test@demo.mx")).thenReturn(true);
        when(sesionProps.getPreAuthTokenMinutos()).thenReturn(5);
        when(jwtService.generarPreAuthToken(1L, "test@demo.mx", 5L)).thenReturn("pre-auth-abc");

        LoginResponse r = service.login(new LoginRequest("test@demo.mx", "ok"), false, "ua", "127.0.0.1");

        assertThat(r.requiere2FA()).isTrue();
        assertThat(r.preAuthToken()).isEqualTo("pre-auth-abc");
        assertThat(r.token()).isNull();
        // IMPORTANTE: en etapa 1 NO se debe crear sesion server-side
        Mockito.verifyNoInteractions(sesionService);
    }

    @Test
    void verificar2FA_codigoValido_emiteAccessToken() {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("test@demo.mx");
        when(jwtService.validarPreAuthToken("pre-auth-abc")).thenReturn(claims);
        when(usuarioRepository.findByCorreo("test@demo.mx")).thenReturn(Optional.of(usuario));
        when(totpService.validarCodigo("test@demo.mx", "123456")).thenReturn(true);
        when(sesionService.crearSesion(anyLong(), anyString(), any(), any(), anyBoolean()))
                .thenReturn(SesionActiva.builder().id(1L).jti("jti-nuevo").build());
        when(jwtService.generarToken(1L, "test@demo.mx", "jti-nuevo")).thenReturn("tok-real");
        when(jwtService.getExpirationSeconds()).thenReturn(3600L);

        LoginResponse r = service.verificar2FA(
                new Totp2FAVerificarRequest("pre-auth-abc", "123456"), false, "ua", "ip");

        assertThat(r.token()).isEqualTo("tok-real");
        assertThat(r.requiere2FA()).isFalse();
        assertThat(r.preAuthToken()).isNull();
    }

    @Test
    void verificar2FA_preAuthInvalido_lanza() {
        when(jwtService.validarPreAuthToken("bad"))
                .thenThrow(new JwtException("firma invalida"));

        assertThatThrownBy(() -> service.verificar2FA(
                new Totp2FAVerificarRequest("bad", "123456"), false, "ua", "ip"))
                .isInstanceOf(PreAuthTokenInvalidoException.class);
    }

    @Test
    void verificar2FA_codigoInvalido_lanza() {
        Claims claims = mock(Claims.class);
        when(claims.getSubject()).thenReturn("test@demo.mx");
        when(jwtService.validarPreAuthToken("pre-auth-abc")).thenReturn(claims);
        when(usuarioRepository.findByCorreo("test@demo.mx")).thenReturn(Optional.of(usuario));
        when(totpService.validarCodigo("test@demo.mx", "000000")).thenReturn(false);

        assertThatThrownBy(() -> service.verificar2FA(
                new Totp2FAVerificarRequest("pre-auth-abc", "000000"), false, "ua", "ip"))
                .isInstanceOf(TotpCodigoInvalidoException.class);
    }
}
