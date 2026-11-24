package com.proyecto.servicios.onboarding.transaccion.client;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.proyecto.servicios.onboarding.transaccion.client.dto.PayloadSendTx;
import com.proyecto.servicios.onboarding.transaccion.client.dto.ResultadoTx;
import com.proyecto.servicios.onboarding.transaccion.config.GestopagoProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests end-to-end del GestopagoTxGateway contra un WireMock local. Cubre:
 *   - sendTx aprobado: parseo correcto del XML y Resultado.RESPUESTA aprobada.
 *   - sendTx rechazado codigo 44: Resultado.RESPUESTA no aprobada.
 *   - 5xx: Resultado.SIN_RESPUESTA (DESCONOCIDO).
 *   - 401 primero, luego 200: renovacion automatica del token.
 *   - XML invalido: SIN_RESPUESTA.
 *   - Sin cifrado: NO_ENVIADA.
 *   - **El token no aparece en los logs capturados.**
 */
class GestopagoTxGatewayTest {

    private static final String LLAVE_PRUEBA = "HPo7OLqB4Fkk4E2yGOtwqw8H5fHR9kNx67OR4g4UdlA=";
    private static final String IV_PRUEBA    = "p5ldmBPdd/9pjC0bDC/nSg==";
    private static final String TOKEN_SECRETO = "TOK-ULTRA-SECRETO-2026";

    private WireMockServer wireMock;
    private GestopagoTxGateway gateway;
    private GestopagoAuthClient authClient;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger gatewayLogger;

    @BeforeEach
    void setup() throws Exception {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();

        GestopagoProperties props = new GestopagoProperties();
        props.setEnabled(true);
        props.getTx().setUrl("http://localhost:" + wireMock.port());
        props.getTx().setUnidad("083");
        props.getCifrado().setLlave(LLAVE_PRUEBA);
        props.getCifrado().setIv(IV_PRUEBA);

        var settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(Duration.ofSeconds(2))
                .withReadTimeout(Duration.ofSeconds(3));
        RestClient rc = RestClient.builder().baseUrl(props.getTx().getUrl())
                .requestFactory(ClientHttpRequestFactories.get(settings)).build();

        CifradoGestopago cifrado = new CifradoGestopago(props);
        Clock clock = Clock.fixed(Instant.parse("2026-11-03T12:00:00Z"), ZoneOffset.UTC);

        authClient = mock(GestopagoAuthClient.class);
        when(authClient.obtenerTokenVigente()).thenReturn(Optional.of(TOKEN_SECRETO));
        when(authClient.renovar()).thenReturn(Optional.of(TOKEN_SECRETO + "-RENOVADO"));

        gateway = new GestopagoTxGateway(rc, authClient, cifrado, new ObjectMapper(), props, clock);

        gatewayLogger = (Logger) LoggerFactory.getLogger(GestopagoTxGateway.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        gatewayLogger.addAppender(logAppender);
        gatewayLogger.setLevel(Level.TRACE);
    }

    @AfterEach
    void tearDown() {
        if (gatewayLogger != null && logAppender != null) gatewayLogger.detachAppender(logAppender);
        if (wireMock != null) wireMock.stop();
    }

    @Test
    void enviar_aprobada_devuelveResultadoAprobado() {
        wireMock.stubFor(post("/sistema/service/sendTx.do")
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody("""
                                <RESPONSE>
                                  <ID_TX>TX-123</ID_TX>
                                  <NUM_AUTORIZACION>555</NUM_AUTORIZACION>
                                  <MENSAJE><CODIGO>01</CODIGO><TEXTO>ok</TEXTO></MENSAJE>
                                </RESPONSE>""")));

        ResultadoTx r = gateway.enviar(payloadDemo());

        assertThat(r.tipo()).isEqualTo(ResultadoTx.Tipo.RESPUESTA);
        assertThat(r.esAprobada()).isTrue();
        assertThat(r.autorizacion().numAutorizacionLong()).isEqualTo(555L);

        wireMock.verify(postRequestedFor(urlPathEqualTo("/sistema/service/sendTx.do"))
                .withHeader("Authorization", equalTo("Bearer " + TOKEN_SECRETO))
                .withHeader("Content-Type", containing("application/x-www-form-urlencoded")));

        verificarTokenNoEnLogs();
    }

    @Test
    void enviar_rechazada_devuelveResultadoNoAprobado() {
        wireMock.stubFor(post("/sistema/service/sendTx.do")
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody("""
                                <RESPONSE>
                                  <NUM_AUTORIZACION>-1</NUM_AUTORIZACION>
                                  <MENSAJE><CODIGO>44</CODIGO><TEXTO>Servicio no asignado</TEXTO></MENSAJE>
                                </RESPONSE>""")));

        ResultadoTx r = gateway.enviar(payloadDemo());

        assertThat(r.tipo()).isEqualTo(ResultadoTx.Tipo.RESPUESTA);
        assertThat(r.esAprobada()).isFalse();
        assertThat(r.autorizacion().codigo()).isEqualTo("44");
        verificarTokenNoEnLogs();
    }

    @Test
    void enviar_5xx_devuelveSinRespuesta() {
        wireMock.stubFor(post("/sistema/service/sendTx.do")
                .willReturn(aResponse().withStatus(503)));

        ResultadoTx r = gateway.enviar(payloadDemo());
        assertThat(r.tipo()).isEqualTo(ResultadoTx.Tipo.SIN_RESPUESTA);
        verificarTokenNoEnLogs();
    }

    @Test
    void enviar_401_renuevaTokenYReintenta() {
        wireMock.stubFor(post("/sistema/service/sendTx.do")
                .withHeader("Authorization", equalTo("Bearer " + TOKEN_SECRETO))
                .willReturn(aResponse().withStatus(401)));
        wireMock.stubFor(post("/sistema/service/sendTx.do")
                .withHeader("Authorization", equalTo("Bearer " + TOKEN_SECRETO + "-RENOVADO"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody("""
                                <RESPONSE>
                                  <NUM_AUTORIZACION>1</NUM_AUTORIZACION>
                                  <MENSAJE><CODIGO>01</CODIGO><TEXTO>ok</TEXTO></MENSAJE>
                                </RESPONSE>""")));

        ResultadoTx r = gateway.enviar(payloadDemo());

        assertThat(r.esAprobada()).isTrue();
        verify(authClient).renovar();
        wireMock.verify(2, postRequestedFor(urlPathEqualTo("/sistema/service/sendTx.do")));
    }

    @Test
    void enviar_xmlInvalido_devuelveSinRespuesta() {
        wireMock.stubFor(post("/sistema/service/sendTx.do")
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html><body>404 File not found</body></html>")));

        ResultadoTx r = gateway.enviar(payloadDemo());
        assertThat(r.tipo()).isEqualTo(ResultadoTx.Tipo.SIN_RESPUESTA);
    }

    @Test
    void confirmar_llamaEndpointConfirmTx() {
        wireMock.stubFor(post("/sistema/service/confirmTx.do")
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody("""
                                <RESPONSE>
                                  <NUM_AUTORIZACION>42</NUM_AUTORIZACION>
                                  <MENSAJE><CODIGO>06</CODIGO><TEXTO>Aplicada</TEXTO></MENSAJE>
                                </RESPONSE>""")));

        ResultadoTx r = gateway.confirmar(payloadDemo());
        assertThat(r.esAprobada()).isTrue();
        assertThat(r.autorizacion().codigo()).isEqualTo("06");
    }

    private PayloadSendTx payloadDemo() {
        return PayloadSendTx.builder()
                .idServicio("TELCEL").idProducto("TELCEL_100")
                .montoPago(new BigDecimal("100.00"))
                .telefono("5512345678")
                .horaLocal("20261103120000")
                .upc("UPC12345AB").unidad("083").build();
    }

    private void verificarTokenNoEnLogs() {
        List<ILoggingEvent> events = logAppender.list;
        for (ILoggingEvent e : events) {
            String msg = e.getFormattedMessage();
            assertThat(msg)
                    .as("Token no debe aparecer en log: '%s'", msg)
                    .doesNotContain(TOKEN_SECRETO);
        }
    }
}
