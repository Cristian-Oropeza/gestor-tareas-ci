package com.proyecto.servicios.onboarding.transaccion.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RestClientsConfigTest {

    @Test
    void beans_authYTxSeConstruyenSinError() {
        GestopagoProperties props = new GestopagoProperties();
        props.getAuth().setUrl("https://proveedor.test");
        props.getTx().setUrl("https://proveedor.test");
        props.getTx().setConnectTimeout(Duration.ofSeconds(5));
        props.getTx().setReadTimeout(Duration.ofSeconds(10));

        RestClientsConfig cfg = new RestClientsConfig(props);
        RestClient auth = cfg.gestopagoAuthRestClient();
        RestClient tx = cfg.gestopagoTxRestClient();

        assertThat(auth).isNotNull();
        assertThat(tx).isNotNull();
    }
}
