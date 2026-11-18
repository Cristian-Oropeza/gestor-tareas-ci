package com.proyecto.servicios.onboarding.transaccion.client.xml;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que el JAXB mapea correctamente el XML real que devuelve GestoPago/PuntoRed.
 * El root es RESPONSE (no AUTORIZACION como se creia inicialmente) y MENSAJE es un elemento
 * anidado con CODIGO/TEXTO/ID_TX.
 */
class AutorizacionXmlTest {

    private static Unmarshaller unmarshaller;

    @BeforeAll
    static void setup() throws Exception {
        unmarshaller = JAXBContext.newInstance(AutorizacionXml.class).createUnmarshaller();
    }

    @Test
    void parseRespuestaAprobada_mapeaTodosLosCampos() throws Exception {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <RESPONSE>
                  <ID_TX>TX-99999</ID_TX>
                  <NUM_AUTORIZACION>123456</NUM_AUTORIZACION>
                  <SALDO>1500.50</SALDO>
                  <COMISION>0.00</COMISION>
                  <FECHA>20261103000000</FECHA>
                  <MONTO>100.00</MONTO>
                  <MENSAJE>
                    <CODIGO>01</CODIGO>
                    <TEXTO>Operacion exitosa</TEXTO>
                    <REFERENCIA>REF-ABC</REFERENCIA>
                  </MENSAJE>
                </RESPONSE>
                """;
        AutorizacionXml r = (AutorizacionXml) unmarshaller.unmarshal(new StringReader(xml));

        assertThat(r.getIdTx()).isEqualTo("TX-99999");
        assertThat(r.codigo()).isEqualTo("01");
        assertThat(r.texto()).isEqualTo("Operacion exitosa");
        assertThat(r.numAutorizacionLong()).isEqualTo(123456L);
        assertThat(r.esAprobada()).isTrue();
        assertThat(r.idTransaccion()).isEqualTo("TX-99999");
    }

    @Test
    void parseRespuestaRechazada_noEsAprobada() throws Exception {
        String xml = """
                <RESPONSE>
                  <NUM_AUTORIZACION>-1</NUM_AUTORIZACION>
                  <MENSAJE>
                    <CODIGO>44</CODIGO>
                    <TEXTO>Servicio no asignado</TEXTO>
                  </MENSAJE>
                </RESPONSE>
                """;
        AutorizacionXml r = (AutorizacionXml) unmarshaller.unmarshal(new StringReader(xml));
        assertThat(r.codigo()).isEqualTo("44");
        assertThat(r.esAprobada()).isFalse();
        assertThat(r.numAutorizacionLong()).isEqualTo(-1L);
    }

    @Test
    void confirmTx_idTxDentroDeMensaje() throws Exception {
        String xml = """
                <RESPONSE>
                  <NUM_AUTORIZACION>555</NUM_AUTORIZACION>
                  <MENSAJE>
                    <CODIGO>06</CODIGO>
                    <TEXTO>Aplicada</TEXTO>
                    <ID_TX>TX-FROM-MENSAJE</ID_TX>
                  </MENSAJE>
                </RESPONSE>
                """;
        AutorizacionXml r = (AutorizacionXml) unmarshaller.unmarshal(new StringReader(xml));
        assertThat(r.idTransaccion()).isEqualTo("TX-FROM-MENSAJE");
        assertThat(r.esAprobada()).isTrue(); // codigo 06 tambien cuenta
    }

    @Test
    void numAutorizacionNoParseable_devuelveNull() throws Exception {
        String xml = """
                <RESPONSE>
                  <NUM_AUTORIZACION>N/A</NUM_AUTORIZACION>
                  <MENSAJE><CODIGO>82</CODIGO><TEXTO>Timeout</TEXTO></MENSAJE>
                </RESPONSE>
                """;
        AutorizacionXml r = (AutorizacionXml) unmarshaller.unmarshal(new StringReader(xml));
        assertThat(r.numAutorizacionLong()).isNull();
        assertThat(r.esAprobada()).isFalse();
    }
}
