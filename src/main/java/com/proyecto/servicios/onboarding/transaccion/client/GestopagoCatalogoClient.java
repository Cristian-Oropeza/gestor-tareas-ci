package com.proyecto.servicios.onboarding.transaccion.client;

import com.proyecto.servicios.onboarding.transaccion.config.GestopagoProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

/**
 * Cliente simple para GestoPago getProductList.do. Reutiliza el RestClient
 * de transacciones. Si el proveedor falla, devuelve Optional.empty() y el
 * scheduler deja el catalogo local intacto (no borra nada).
 *
 * Nota: se evita Lombok @RequiredArgsConstructor para que el @Qualifier viaje
 * en el constructor (necesario porque hay 2 beans RestClient en el contexto).
 */
@Component
@Slf4j
public class GestopagoCatalogoClient {

    private final RestClient restClient;
    private final GestopagoAuthClient authClient;
    private final GestopagoProperties props;

    public GestopagoCatalogoClient(@Qualifier("gestopagoTxRestClient") RestClient restClient,
                                   GestopagoAuthClient authClient,
                                   GestopagoProperties props) {
        this.restClient = restClient;
        this.authClient = authClient;
        this.props = props;
    }

    /** Devuelve el XML crudo del catalogo o empty si algo fallo. */
    public Optional<String> obtenerCatalogoXml() {
        String token = authClient.obtenerTokenVigente().orElse(null);
        if (token == null) {
            log.warn("Catalogo sync: sin token vigente de GestoPago");
            return Optional.empty();
        }
        try {
            String body = restClient.get()
                    .uri(props.getCatalogo().getPath())
                    .header("Authorization", "Bearer " + token)
                    .accept(MediaType.APPLICATION_XML)
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                log.warn("Catalogo sync: respuesta vacia de GestoPago");
                return Optional.empty();
            }
            return Optional.of(body);
        } catch (RestClientException ex) {
            log.warn("Catalogo sync: error HTTP {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
