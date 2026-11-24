package com.proyecto.servicios.onboarding.transaccion.client;

import com.proyecto.servicios.onboarding.transaccion.config.GestopagoProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitarios para CifradoGestopago. Usa las llaves publicas de prueba del distribuidor 83.
 * Verifica:
 *   - Round-trip cifrar/descifrar preserva el contenido.
 *   - Sin configuracion no cifra.
 *   - Llaves mal formadas fallan al arrancar.
 */
class CifradoGestopagoTest {

    private static final String LLAVE_PRUEBA = "HPo7OLqB4Fkk4E2yGOtwqw8H5fHR9kNx67OR4g4UdlA=";
    private static final String IV_PRUEBA    = "p5ldmBPdd/9pjC0bDC/nSg==";

    private GestopagoProperties propsConCifrado() {
        GestopagoProperties p = new GestopagoProperties();
        p.getCifrado().setLlave(LLAVE_PRUEBA);
        p.getCifrado().setIv(IV_PRUEBA);
        return p;
    }

    @Test
    void roundTrip_cifrarYDescifrarRetornaElMismoTexto() {
        CifradoGestopago c = new CifradoGestopago(propsConCifrado());
        String plano = "{\"idProducto\":\"TELCEL_100\",\"monto\":100.00}";

        String cifrado = c.cifrar(plano);
        String descifrado = c.descifrar(cifrado);

        assertThat(cifrado).isNotEqualTo(plano).isNotBlank();
        assertThat(descifrado).isEqualTo(plano);
    }

    @Test
    void sinConfiguracion_noCifradoReporta() {
        GestopagoProperties p = new GestopagoProperties();
        CifradoGestopago c = new CifradoGestopago(p);
        assertThat(c.configurado()).isFalse();

        assertThatThrownBy(() -> c.cifrar("algo"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void llaveConTamanoIncorrecto_fallaAlConstruir() {
        GestopagoProperties p = new GestopagoProperties();
        p.getCifrado().setLlave("c2hvcnQ="); // "short" en base64 = 5 bytes != 32
        p.getCifrado().setIv(IV_PRUEBA);

        assertThatThrownBy(() -> new CifradoGestopago(p))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void ivConTamanoIncorrecto_fallaAlConstruir() {
        GestopagoProperties p = new GestopagoProperties();
        p.getCifrado().setLlave(LLAVE_PRUEBA);
        p.getCifrado().setIv("c2hvcnQ="); // 5 bytes != 16

        assertThatThrownBy(() -> new CifradoGestopago(p))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("16 bytes");
    }

    @Test
    void llaveNoBase64_fallaAlConstruir() {
        GestopagoProperties p = new GestopagoProperties();
        p.getCifrado().setLlave("!!!no es base64!!!");
        p.getCifrado().setIv(IV_PRUEBA);

        assertThatThrownBy(() -> new CifradoGestopago(p))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }
}
