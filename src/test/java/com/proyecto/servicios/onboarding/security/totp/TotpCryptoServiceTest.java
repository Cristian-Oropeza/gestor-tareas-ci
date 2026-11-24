package com.proyecto.servicios.onboarding.security.totp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TotpCryptoServiceTest {

    private TotpCryptoService service;
    private TotpProperties props;

    private static String key32base64() {
        byte[] bytes = new byte[32];
        for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) i;
        return Base64.getEncoder().encodeToString(bytes);
    }

    @BeforeEach
    void setUp() {
        props = new TotpProperties();
        props.setEncryptionKey(key32base64());
        service = new TotpCryptoService(props);
        service.init();
    }

    @Test
    void roundtrip_cifrarYDescifrar_devuelveOriginal() {
        String plano = "JBSWY3DPEHPK3PXP";
        String cifrado = service.cifrar(plano);

        assertThat(cifrado).isNotEqualTo(plano);
        assertThat(cifrado.length()).isGreaterThan(plano.length());
        assertThat(service.descifrar(cifrado)).isEqualTo(plano);
    }

    @Test
    void cifrar_mismoPlaintext_produceCiphertextsDistintos_porqueIVesAleatorio() {
        String plano = "JBSWY3DPEHPK3PXP";
        String c1 = service.cifrar(plano);
        String c2 = service.cifrar(plano);
        assertThat(c1).isNotEqualTo(c2);
        assertThat(service.descifrar(c1)).isEqualTo(plano);
        assertThat(service.descifrar(c2)).isEqualTo(plano);
    }

    @Test
    void init_llaveVacia_falla() {
        TotpProperties bad = new TotpProperties();
        bad.setEncryptionKey("");
        TotpCryptoService s = new TotpCryptoService(bad);
        assertThatThrownBy(s::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TOTP_SECRET_ENCRYPTION_KEY");
    }

    @Test
    void init_llaveLongitudIncorrecta_falla() {
        TotpProperties bad = new TotpProperties();
        bad.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[16])); // AES-128
        TotpCryptoService s = new TotpCryptoService(bad);
        assertThatThrownBy(s::init)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void init_llaveNoBase64_falla() {
        TotpProperties bad = new TotpProperties();
        bad.setEncryptionKey("esto no es base64 valido $$$ <<< >>>");
        TotpCryptoService s = new TotpCryptoService(bad);
        assertThatThrownBy(s::init).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void descifrar_payloadCorto_falla() {
        assertThatThrownBy(() -> service.descifrar("YQ=="))  // 1 byte decodificado
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void descifrar_payloadManipulado_falla_porAutenticacionGCM() {
        String plano = "SECRET123";
        String cifrado = service.cifrar(plano);
        // Alterar el ultimo caracter (tag GCM fallara)
        char[] chars = cifrado.toCharArray();
        chars[chars.length - 2] = chars[chars.length - 2] == 'A' ? 'B' : 'A';
        String tampered = new String(chars);
        assertThatThrownBy(() -> service.descifrar(tampered))
                .isInstanceOf(IllegalStateException.class);
    }
}
