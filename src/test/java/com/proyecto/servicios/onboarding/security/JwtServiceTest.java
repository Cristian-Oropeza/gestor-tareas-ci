package com.proyecto.servicios.onboarding.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitarios de JwtService. Cubre: generacion, validacion, fallback por secret vacio
 * y configuracion de expiracion.
 */
class JwtServiceTest {

    private JwtService service;

    @BeforeEach
    void setup() {
        service = new JwtService();
        ReflectionTestUtils.setField(service, "secret", "un-secreto-valido-de-al-menos-32-caracteres-123456");
        ReflectionTestUtils.setField(service, "expirationMs", 3600000L);
        ReflectionTestUtils.setField(service, "issuer", "onboarding-clientes");
        ReflectionTestUtils.invokeMethod(service, "init");
    }

    @Test
    void generarYValidar_roundTripOk() {
        String token = service.generarToken(42L, "test@demo.mx");
        Claims claims = service.validar(token);

        assertThat(claims.getSubject()).isEqualTo("test@demo.mx");
        assertThat(claims.get("clienteId", Long.class)).isEqualTo(42L);
        assertThat(claims.getIssuer()).isEqualTo("onboarding-clientes");
    }

    @Test
    void validar_tokenInvalido_lanzaExcepcion() {
        assertThatThrownBy(() -> service.validar("token.basura.invalido"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void init_conSecretVacio_usaFallback() {
        JwtService nuevo = new JwtService();
        ReflectionTestUtils.setField(nuevo, "secret", "");
        ReflectionTestUtils.setField(nuevo, "expirationMs", 3600000L);
        ReflectionTestUtils.setField(nuevo, "issuer", "test");
        ReflectionTestUtils.invokeMethod(nuevo, "init");
        // El fallback permite generar/validar normalmente
        String tok = nuevo.generarToken(1L, "x@x.mx");
        assertThat(nuevo.validar(tok).getSubject()).isEqualTo("x@x.mx");
    }

    @Test
    void init_conSecretPlaceholder_usaFallback() {
        JwtService nuevo = new JwtService();
        ReflectionTestUtils.setField(nuevo, "secret", "${JWT_SECRET}");
        ReflectionTestUtils.setField(nuevo, "expirationMs", 3600000L);
        ReflectionTestUtils.setField(nuevo, "issuer", "test");
        ReflectionTestUtils.invokeMethod(nuevo, "init");
        assertThat(nuevo.getExpirationSeconds()).isEqualTo(3600L);
    }

    @Test
    void init_conSecretCorto_padea() {
        JwtService nuevo = new JwtService();
        ReflectionTestUtils.setField(nuevo, "secret", "corto");
        ReflectionTestUtils.setField(nuevo, "expirationMs", 7200000L);
        ReflectionTestUtils.setField(nuevo, "issuer", "test");
        ReflectionTestUtils.invokeMethod(nuevo, "init");
        // aun con secret corto, el servicio funciona (padea a 32)
        String tok = nuevo.generarToken(1L, "u@test.mx");
        assertThat(nuevo.validar(tok)).isNotNull();
    }

    @Test
    void getExpirationSeconds_devuelveConvertido() {
        assertThat(service.getExpirationSeconds()).isEqualTo(3600L);
    }
}
