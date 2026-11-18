package com.proyecto.servicios.onboarding.transaccion.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.proyecto.servicios.onboarding.transaccion.config.GestopagoProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de GestopagoAuthClient con WireMock como servidor fake de PuntoRed.
 * Verifica:
 *   - Login exitoso devuelve token y lo cachea.
 *   - 404 o error de red devuelve Optional.empty().
 *   - Si el token esta vigente, no se vuelve a llamar al proveedor.
 *   - renovar() fuerza una nueva llamada ignorando la cache.
 */
class GestopagoAuthClientTest {

    private static final String PATH_LOGIN = "/sistema/app/jwt-gp/authenticate/";

    private WireMockServer wireMock;
    private GestopagoAuthClient client;
    private GestopagoProperties props;

    @BeforeEach
    void setup() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();

        props = new GestopagoProperties();
        props.getAuth().setUrl("http://localhost:" + wireMock.port());
        props.getAuth().setPassword("12345678");

        RestClient rc = RestClient.builder().baseUrl(props.getAuth().getUrl()).build();
        Clock clock = Clock.fixed(Instant.parse("2026-11-03T00:00:00Z"), ZoneOffset.UTC);
        client = new GestopagoAuthClient(rc, props, clock);
    }

    @AfterEach
    void tearDown() {
        if (wireMock != null) wireMock.stop();
    }

    @Test
    void obtenerTokenVigente_devuelveTokenEnLoginExitoso() {
        wireMock.stubFor(post(urlPathEqualTo(PATH_LOGIN))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"token\":\"TOK-ABC-123\",\"expires_in\":86400}")));

        Optional<String> token = client.obtenerTokenVigente();

        assertThat(token).contains("TOK-ABC-123");
        wireMock.verify(1, postRequestedFor(urlPathEqualTo(PATH_LOGIN))
                .withQueryParam("idDistribuidor",   equalTo("83"))
                .withQueryParam("codigoDispositivo", equalTo("GPS83-TPV-17"))
                .withQueryParam("password",         equalTo("12345678")));
    }

    @Test
    void obtenerTokenVigente_usaCacheEnSegundaLlamada() {
        wireMock.stubFor(post(urlPathEqualTo(PATH_LOGIN))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"token\":\"TOK-CACHED\",\"expires_in\":86400}")));

        Optional<String> a = client.obtenerTokenVigente();
        Optional<String> b = client.obtenerTokenVigente();
        Optional<String> c = client.obtenerTokenVigente();

        assertThat(a).contains("TOK-CACHED");
        assertThat(b).contains("TOK-CACHED");
        assertThat(c).contains("TOK-CACHED");
        wireMock.verify(1, postRequestedFor(urlPathEqualTo(PATH_LOGIN))); // solo UNA llamada
    }

    @Test
    void renovar_fuerzaNuevaLlamadaIgnorandoCache() {
        wireMock.stubFor(post(urlPathEqualTo(PATH_LOGIN))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"token\":\"TOK-X\",\"expires_in\":86400}")));

        client.obtenerTokenVigente(); // primera
        client.invalidar();            // invalidamos cache
        client.renovar();              // debe pedir de nuevo

        wireMock.verify(2, postRequestedFor(urlPathEqualTo(PATH_LOGIN)));
    }

    @Test
    void obtenerTokenVigente_devuelveEmptyEn404() {
        wireMock.stubFor(post(urlPathEqualTo(PATH_LOGIN))
                .willReturn(aResponse().withStatus(404).withBody("not found")));

        Optional<String> token = client.obtenerTokenVigente();
        assertThat(token).isEmpty();
    }

    @Test
    void obtenerTokenVigente_devuelveEmptyEnRespuestaSinToken() {
        wireMock.stubFor(post(urlPathEqualTo(PATH_LOGIN))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\":\"sin token\"}")));

        assertThat(client.obtenerTokenVigente()).isEmpty();
    }

    @Test
    void renovar_devuelveEmptySinPasswordConfigurada() {
        props.getAuth().setPassword("");
        Optional<String> token = client.renovar();
        assertThat(token).isEmpty();
        wireMock.verify(0, postRequestedFor(urlPathEqualTo(PATH_LOGIN)));
    }
}
