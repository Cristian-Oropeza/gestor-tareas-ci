package com.proyecto.servicios.onboarding.transaccion.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GestopagoPropertiesTest {

    @Test
    void validar_disabled_noLanza() {
        GestopagoProperties p = new GestopagoProperties();
        p.setEnabled(false);
        assertThatCode(() -> invokeValidar(p)).doesNotThrowAnyException();
    }

    @Test
    void validar_enabledConTodoOk_noLanza() {
        assertThatCode(() -> invokeValidar(enabledMinOk())).doesNotThrowAnyException();
    }

    @Test
    void validar_unidadCortaLanza() {
        GestopagoProperties p = enabledMinOk();
        p.getTx().setUnidad("AB");
        assertThatThrownBy(() -> invokeValidar(p))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unidad");
    }

    @Test
    void validar_unidadLargaLanza() {
        GestopagoProperties p = enabledMinOk();
        p.getTx().setUnidad("A".repeat(26));
        assertThatThrownBy(() -> invokeValidar(p))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validar_urlConPlaceholderLanza() {
        GestopagoProperties p = enabledMinOk();
        p.getAuth().setUrl("${GESTOPAGO_URL}");
        assertThatThrownBy(() -> invokeValidar(p))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("placeholder");
    }

    @Test
    void validar_sinCifrado_soloWarn() {
        GestopagoProperties p = enabledMinOk();
        p.getCifrado().setLlave("");
        p.getCifrado().setIv("");
        assertThatCode(() -> invokeValidar(p)).doesNotThrowAnyException();
    }

    @Test
    void propertySettersYGetters_funcionan() {
        GestopagoProperties p = new GestopagoProperties();
        p.setEnabled(true);
        p.setApiKey("api-key-test");
        p.getAuth().setIdDistribuidor("99");
        p.getAuth().setCodigoDispositivo("DISP-99");
        p.getTx().setZonaHoraria("America/Mexico_City");

        assertThatCode(() -> {
            if (!p.isEnabled()) throw new RuntimeException();
            if (!"api-key-test".equals(p.getApiKey())) throw new RuntimeException();
            if (!"99".equals(p.getAuth().getIdDistribuidor())) throw new RuntimeException();
        }).doesNotThrowAnyException();
    }

    private GestopagoProperties enabledMinOk() {
        GestopagoProperties p = new GestopagoProperties();
        p.setEnabled(true);
        p.getAuth().setUrl("https://proveedor.test");
        p.getAuth().setPassword("12345678");
        p.getTx().setUrl("https://proveedor.test");
        p.getTx().setUnidad("083");
        p.getCifrado().setLlave("HPo7OLqB4Fkk4E2yGOtwqw8H5fHR9kNx67OR4g4UdlA=");
        p.getCifrado().setIv("p5ldmBPdd/9pjC0bDC/nSg==");
        return p;
    }

    private void invokeValidar(GestopagoProperties p) {
        ReflectionTestUtils.invokeMethod(p, "validar");
    }
}
