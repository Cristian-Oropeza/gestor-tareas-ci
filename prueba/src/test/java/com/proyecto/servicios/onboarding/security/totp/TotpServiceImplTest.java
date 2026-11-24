package com.proyecto.servicios.onboarding.security.totp;

import com.proyecto.servicios.onboarding.dto.response.Totp2FASetupResponse;
import com.proyecto.servicios.onboarding.entity.Cliente;
import com.proyecto.servicios.onboarding.entity.Usuario;
import com.proyecto.servicios.onboarding.entity.UsuarioTotp;
import com.proyecto.servicios.onboarding.exception.CredencialesInvalidasException;
import com.proyecto.servicios.onboarding.exception.TotpBloqueadoException;
import com.proyecto.servicios.onboarding.exception.TotpCodigoInvalidoException;
import com.proyecto.servicios.onboarding.exception.TotpNoActivadoException;
import com.proyecto.servicios.onboarding.exception.TotpNoConfiguradoException;
import com.proyecto.servicios.onboarding.exception.TotpYaActivadoException;
import com.proyecto.servicios.onboarding.repository.UsuarioRepository;
import com.proyecto.servicios.onboarding.repository.UsuarioTotpRepository;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TotpServiceImplTest {

    @Mock UsuarioRepository usuarioRepository;
    @Mock UsuarioTotpRepository usuarioTotpRepository;
    @Mock PasswordEncoder passwordEncoder;

    TotpCryptoService cryptoService;
    TotpProperties props;
    Clock clock;
    TotpServiceImpl service;

    Usuario usuario;

    private static String keyBase64() {
        byte[] b = new byte[32];
        for (int i = 0; i < b.length; i++) b[i] = (byte)(i + 1);
        return Base64.getEncoder().encodeToString(b);
    }

    @BeforeEach
    void setUp() {
        props = new TotpProperties();
        props.setEncryptionKey(keyBase64());
        props.setMaxIntentos(3);
        props.setBloqueoMinutos(15);
        props.setScratchCodesCount(5);

        cryptoService = new TotpCryptoService(props);
        cryptoService.init();

        clock = Clock.fixed(Instant.parse("2026-11-23T20:00:00Z"), ZoneOffset.UTC);

        service = new TotpServiceImpl(
                usuarioRepository, usuarioTotpRepository,
                cryptoService, props, passwordEncoder, clock);

        usuario = Usuario.builder()
                .id(10L).correo("test@demo.mx").password("$2a$12$hash").activo(Boolean.TRUE)
                .cliente(Cliente.builder().id(1L).activo(Boolean.TRUE).build())
                .build();
    }

    // =================== iniciarSetup ===================

    @Test
    void iniciarSetup_sinRowPrevio_creaYDevuelveQrConScratchCodes() {
        when(usuarioRepository.findByCorreo("test@demo.mx")).thenReturn(Optional.of(usuario));
        when(usuarioTotpRepository.findByUsuarioCorreo("test@demo.mx")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "hash-" + inv.getArgument(0));

        Totp2FASetupResponse r = service.iniciarSetup("test@demo.mx");

        assertThat(r.issuer()).isEqualTo("GestoPago Comercio PWA Oropeza");
        assertThat(r.label()).isEqualTo("test@demo.mx");
        assertThat(r.otpauthUri()).startsWith("otpauth://totp/");
        assertThat(r.qrImageDataUri()).startsWith("data:image/png;base64,");
        assertThat(r.scratchCodes()).hasSize(5);
        assertThat(r.scratchCodes()).allMatch(s -> s.length() == 10);

        ArgumentCaptor<UsuarioTotp> cap = ArgumentCaptor.forClass(UsuarioTotp.class);
        verify(usuarioTotpRepository).save(cap.capture());
        UsuarioTotp saved = cap.getValue();
        assertThat(saved.getActivado()).isFalse();
        assertThat(saved.getSecretCifrado()).isNotBlank();
        assertThat(saved.getScratchCodes()).contains(",");
    }

    @Test
    void iniciarSetup_yaActivado_falla() {
        UsuarioTotp ya = UsuarioTotp.builder().usuario(usuario).activado(Boolean.TRUE).build();
        when(usuarioTotpRepository.findByUsuarioCorreo("test@demo.mx")).thenReturn(Optional.of(ya));
        when(usuarioRepository.findByCorreo("test@demo.mx")).thenReturn(Optional.of(usuario));

        assertThatThrownBy(() -> service.iniciarSetup("test@demo.mx"))
                .isInstanceOf(TotpYaActivadoException.class);
    }

    @Test
    void iniciarSetup_setupPrevioNoActivado_regenera() {
        UsuarioTotp previo = UsuarioTotp.builder()
                .usuario(usuario)
                .secretCifrado(cryptoService.cifrar("OLDSECRET123"))
                .scratchCodes("hash1,hash2")
                .activado(Boolean.FALSE)
                .intentosFallidos((short) 2)
                .build();
        when(usuarioRepository.findByCorreo("test@demo.mx")).thenReturn(Optional.of(usuario));
        when(usuarioTotpRepository.findByUsuarioCorreo("test@demo.mx")).thenReturn(Optional.of(previo));
        when(passwordEncoder.encode(anyString())).thenAnswer(inv -> "h-" + inv.getArgument(0));

        service.iniciarSetup("test@demo.mx");

        ArgumentCaptor<UsuarioTotp> cap = ArgumentCaptor.forClass(UsuarioTotp.class);
        verify(usuarioTotpRepository).save(cap.capture());
        assertThat(cap.getValue().getIntentosFallidos()).isEqualTo((short) 0);
        assertThat(cryptoService.descifrar(cap.getValue().getSecretCifrado())).isNotEqualTo("OLDSECRET123");
    }

    // =================== activar ===================

    @Test
    void activar_sinSetupPrevio_falla() {
        when(usuarioTotpRepository.findByUsuarioCorreo("test@demo.mx")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.activar("test@demo.mx", "123456"))
                .isInstanceOf(TotpNoConfiguradoException.class);
    }

    @Test
    void activar_yaActivado_falla() {
        UsuarioTotp ya = UsuarioTotp.builder().usuario(usuario).activado(Boolean.TRUE)
                .secretCifrado(cryptoService.cifrar("SECRET")).build();
        when(usuarioTotpRepository.findByUsuarioCorreo("test@demo.mx")).thenReturn(Optional.of(ya));
        assertThatThrownBy(() -> service.activar("test@demo.mx", "123456"))
                .isInstanceOf(TotpYaActivadoException.class);
    }

    @Test
    void activar_codigoInvalido_subeIntentosYLanza() {
        String secret = new GoogleAuthenticator().createCredentials().getKey();
        UsuarioTotp t = UsuarioTotp.builder().usuario(usuario).usuarioId(10L)
                .activado(Boolean.FALSE)
                .secretCifrado(cryptoService.cifrar(secret))
                .intentosFallidos((short) 0).build();
        when(usuarioTotpRepository.findByUsuarioCorreo("test@demo.mx")).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.activar("test@demo.mx", "000000"))
                .isInstanceOf(TotpCodigoInvalidoException.class);
        assertThat(t.getIntentosFallidos()).isEqualTo((short) 1);
    }

    @Test
    void activar_codigoValido_marcaActivado() {
        GoogleAuthenticator ga = new GoogleAuthenticator();
        String secret = ga.createCredentials().getKey();
        int codigo = ga.getTotpPassword(secret);
        UsuarioTotp t = UsuarioTotp.builder().usuario(usuario).usuarioId(10L)
                .activado(Boolean.FALSE)
                .secretCifrado(cryptoService.cifrar(secret))
                .intentosFallidos((short) 0).build();
        when(usuarioTotpRepository.findByUsuarioCorreo("test@demo.mx")).thenReturn(Optional.of(t));

        service.activar("test@demo.mx", String.format("%06d", codigo));

        assertThat(t.getActivado()).isTrue();
        assertThat(t.getIntentosFallidos()).isEqualTo((short) 0);
        assertThat(t.getFechaActivacion()).isNotNull();
    }

    @Test
    void activar_bloqueado_lanzaTotpBloqueado() {
        UsuarioTotp t = UsuarioTotp.builder().usuario(usuario).usuarioId(10L)
                .activado(Boolean.FALSE)
                .secretCifrado(cryptoService.cifrar("XXXX"))
                .bloqueadoHasta(clock.instant().plusSeconds(600).atOffset(ZoneOffset.UTC))
                .intentosFallidos((short) 3).build();
        when(usuarioTotpRepository.findByUsuarioCorreo("test@demo.mx")).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.activar("test@demo.mx", "123456"))
                .isInstanceOf(TotpBloqueadoException.class);
    }

    @Test
    void activar_tercerIntentoFallido_bloqueaCuenta() {
        String secret = new GoogleAuthenticator().createCredentials().getKey();
        UsuarioTotp t = UsuarioTotp.builder().usuario(usuario).usuarioId(10L)
                .activado(Boolean.FALSE)
                .secretCifrado(cryptoService.cifrar(secret))
                .intentosFallidos((short) 2).build();
        when(usuarioTotpRepository.findByUsuarioCorreo("test@demo.mx")).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.activar("test@demo.mx", "000000"))
                .isInstanceOf(TotpCodigoInvalidoException.class);
        assertThat(t.getIntentosFallidos()).isEqualTo((short) 3);
        assertThat(t.getBloqueadoHasta()).isNotNull();
    }

    // =================== desactivar ===================

    @Test
    void desactivar_passwordMal_lanza() {
        when(usuarioRepository.findByCorreo("test@demo.mx")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("mal", usuario.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> service.desactivar("test@demo.mx", "mal"))
                .isInstanceOf(CredencialesInvalidasException.class);
        verify(usuarioTotpRepository, never()).delete(any());
    }

    @Test
    void desactivar_noConfigurado_lanza() {
        when(usuarioRepository.findByCorreo("test@demo.mx")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(usuarioTotpRepository.findByUsuarioCorreo("test@demo.mx")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.desactivar("test@demo.mx", "ok"))
                .isInstanceOf(TotpNoConfiguradoException.class);
    }

    @Test
    void desactivar_noActivado_lanza() {
        UsuarioTotp t = UsuarioTotp.builder().usuario(usuario).activado(Boolean.FALSE).build();
        when(usuarioRepository.findByCorreo("test@demo.mx")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);
        when(usuarioTotpRepository.findByUsuarioCorreo("test@demo.mx")).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> service.desactivar("test@demo.mx", "ok"))
                .isInstanceOf(TotpNoActivadoException.class);
    }

    @Test
    void desactivar_ok_borraRow() {
        UsuarioTotp t = UsuarioTotp.builder().usuario(usuario).activado(Boolean.TRUE).build();
        when(usuarioRepository.findByCorreo("test@demo.mx")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("ok", usuario.getPassword())).thenReturn(true);
        when(usuarioTotpRepository.findByUsuarioCorreo("test@demo.mx")).thenReturn(Optional.of(t));

        service.desactivar("test@demo.mx", "ok");

        verify(usuarioTotpRepository).delete(t);
    }

    // =================== validarCodigo ===================

    @Test
    void validarCodigo_noActivado_lanza() {
        UsuarioTotp t = UsuarioTotp.builder().usuario(usuario).activado(Boolean.FALSE)
                .secretCifrado(cryptoService.cifrar("X")).build();
        when(usuarioTotpRepository.findByUsuarioCorreo("test@demo.mx")).thenReturn(Optional.of(t));
        assertThatThrownBy(() -> service.validarCodigo("test@demo.mx", "123456"))
                .isInstanceOf(TotpNoActivadoException.class);
    }

    @Test
    void validarCodigo_replay_devuelveFalse() {
        UsuarioTotp t = UsuarioTotp.builder().usuario(usuario).usuarioId(10L).activado(Boolean.TRUE)
                .secretCifrado(cryptoService.cifrar("X"))
                .ultimoCodigoUsado("654321").intentosFallidos((short) 0).build();
        when(usuarioTotpRepository.findByUsuarioCorreo("test@demo.mx")).thenReturn(Optional.of(t));

        boolean ok = service.validarCodigo("test@demo.mx", "654321");
        assertThat(ok).isFalse();
    }

    @Test
    void validarCodigo_valido_devuelveTrueYResetea() {
        GoogleAuthenticator ga = new GoogleAuthenticator();
        String secret = ga.createCredentials().getKey();
        String codigo = String.format("%06d", ga.getTotpPassword(secret));
        UsuarioTotp t = UsuarioTotp.builder().usuario(usuario).usuarioId(10L).activado(Boolean.TRUE)
                .secretCifrado(cryptoService.cifrar(secret))
                .intentosFallidos((short) 2).build();
        when(usuarioTotpRepository.findByUsuarioCorreo("test@demo.mx")).thenReturn(Optional.of(t));

        boolean ok = service.validarCodigo("test@demo.mx", codigo);

        assertThat(ok).isTrue();
        assertThat(t.getIntentosFallidos()).isEqualTo((short) 0);
        assertThat(t.getUltimoCodigoUsado()).isEqualTo(codigo);
    }

    @Test
    void validarCodigo_scratchCode_consumeYdevuelveTrue() {
        String scratch = "ABCDE23456";
        String hash = "$2a$12$fake-hash-for-" + scratch;
        UsuarioTotp t = UsuarioTotp.builder().usuario(usuario).usuarioId(10L).activado(Boolean.TRUE)
                .secretCifrado(cryptoService.cifrar("XXX"))
                .scratchCodes(hash + ",other-hash")
                .intentosFallidos((short) 0).build();
        when(usuarioTotpRepository.findByUsuarioCorreo("test@demo.mx")).thenReturn(Optional.of(t));
        when(passwordEncoder.matches(scratch, hash)).thenReturn(true);
        when(passwordEncoder.matches(scratch, "other-hash")).thenReturn(false);

        boolean ok = service.validarCodigo("test@demo.mx", scratch);

        assertThat(ok).isTrue();
        assertThat(t.getScratchCodes()).startsWith("USED:" + hash);
    }
}
