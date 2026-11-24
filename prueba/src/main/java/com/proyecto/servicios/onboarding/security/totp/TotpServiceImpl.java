package com.proyecto.servicios.onboarding.security.totp;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.proyecto.servicios.onboarding.dto.response.Totp2FASetupResponse;
import com.proyecto.servicios.onboarding.entity.Usuario;
import com.proyecto.servicios.onboarding.entity.UsuarioTotp;
import com.proyecto.servicios.onboarding.exception.CredencialesInvalidasException;
import com.proyecto.servicios.onboarding.exception.TotpBloqueadoException;
import com.proyecto.servicios.onboarding.exception.TotpCodigoInvalidoException;
import com.proyecto.servicios.onboarding.exception.TotpNoActivadoException;
import com.proyecto.servicios.onboarding.exception.TotpNoConfiguradoException;
import com.proyecto.servicios.onboarding.exception.TotpYaActivadoException;
import com.proyecto.servicios.onboarding.exception.UsuarioNoEncontradoException;
import com.proyecto.servicios.onboarding.repository.UsuarioRepository;
import com.proyecto.servicios.onboarding.repository.UsuarioTotpRepository;
import com.warrenstrange.googleauth.GoogleAuthenticator;
import com.warrenstrange.googleauth.GoogleAuthenticatorConfig;
import com.warrenstrange.googleauth.GoogleAuthenticatorKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Implementacion de 2FA TOTP compatible con Google Authenticator, Microsoft
 * Authenticator, Authy y cualquier app que siga RFC 6238.
 *
 * Estados por usuario:
 *   - no existe row: 2FA nunca configurado
 *   - row + activado=false: setup iniciado, falta confirmar un codigo
 *   - row + activado=true: 2FA activo, se exige en /auth/login
 *
 * Seguridad:
 *   - El secret base32 solo esta en memoria momentos antes de cifrar y persistir.
 *   - Los scratch codes se devuelven en claro UNA VEZ (en setup); en BD solo BCrypt.
 *   - Lockout: N intentos fallidos consecutivos bloquean X minutos (ver TotpProperties).
 *   - Anti-replay: se guarda el ultimo codigo aceptado para que no se reuse dentro
 *     del mismo paso de 30s (sin importar la ventana configurada).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TotpServiceImpl implements TotpService {

    /** Alfabeto de scratch codes: sin caracteres ambiguos (I, O, 0, 1). */
    private static final char[] SCRATCH_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int SCRATCH_CODE_LEN = 10;
    private static final String SCRATCH_USED_PREFIX = "USED:";

    private final UsuarioRepository usuarioRepository;
    private final UsuarioTotpRepository usuarioTotpRepository;
    private final TotpCryptoService cryptoService;
    private final TotpProperties props;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    private final SecureRandom random = new SecureRandom();

    // ======================================================================
    // Setup
    // ======================================================================

    @Override
    @Transactional
    public Totp2FASetupResponse iniciarSetup(String correo) {
        Usuario usuario = buscarUsuario(correo);
        UsuarioTotp totp = usuarioTotpRepository.findByUsuarioCorreo(correo).orElse(null);

        if (totp != null && Boolean.TRUE.equals(totp.getActivado())) {
            throw new TotpYaActivadoException();
        }

        // Generar nuevo secret + scratch codes (regenera si setup previo quedo pendiente).
        GoogleAuthenticatorKey key = nuevoAuthenticator().createCredentials();
        String secretBase32 = key.getKey();
        List<String> scratchPlanos = generarScratchCodes(props.getScratchCodesCount());
        String scratchHasheados = scratchPlanos.stream()
                .map(passwordEncoder::encode)
                .reduce((a, b) -> a + "," + b)
                .orElse("");

        if (totp == null) {
            totp = UsuarioTotp.builder()
                    .usuario(usuario)
                    .secretCifrado(cryptoService.cifrar(secretBase32))
                    .scratchCodes(scratchHasheados)
                    .activado(Boolean.FALSE)
                    .intentosFallidos((short) 0)
                    .build();
        } else {
            totp.setSecretCifrado(cryptoService.cifrar(secretBase32));
            totp.setScratchCodes(scratchHasheados);
            totp.setIntentosFallidos((short) 0);
            totp.setBloqueadoHasta(null);
            totp.setUltimoCodigoUsado(null);
        }
        usuarioTotpRepository.save(totp);

        String label = correo;
        String otpauthUri = construirOtpauthUri(props.getIssuer(), label, secretBase32);
        String qrDataUri = generarQrDataUri(otpauthUri);

        log.info("2FA setup iniciado para correo={}", correo);
        return new Totp2FASetupResponse(
                props.getIssuer(), label, otpauthUri, qrDataUri, scratchPlanos);
    }

    @Override
    @Transactional
    public void activar(String correo, String codigo) {
        UsuarioTotp totp = usuarioTotpRepository.findByUsuarioCorreo(correo)
                .orElseThrow(TotpNoConfiguradoException::new);

        if (Boolean.TRUE.equals(totp.getActivado())) {
            throw new TotpYaActivadoException();
        }
        verificarBloqueo(totp);

        String secret = cryptoService.descifrar(totp.getSecretCifrado());
        if (!codigoTotpValido(secret, codigo)) {
            registrarIntentoFallido(totp);
            throw new TotpCodigoInvalidoException();
        }
        totp.setActivado(Boolean.TRUE);
        totp.setFechaActivacion(OffsetDateTime.now(clock));
        totp.setIntentosFallidos((short) 0);
        totp.setBloqueadoHasta(null);
        totp.setUltimoCodigoUsado(codigo);
        totp.setFechaUltimoUso(OffsetDateTime.now(clock));
        usuarioTotpRepository.save(totp);
        log.info("2FA activado para correo={}", correo);
    }

    @Override
    @Transactional
    public void desactivar(String correo, String password) {
        Usuario usuario = buscarUsuario(correo);
        if (!passwordEncoder.matches(password, usuario.getPassword())) {
            log.warn("Intento de desactivar 2FA con password incorrecto correo={}", correo);
            throw new CredencialesInvalidasException();
        }
        UsuarioTotp totp = usuarioTotpRepository.findByUsuarioCorreo(correo)
                .orElseThrow(TotpNoConfiguradoException::new);
        if (!Boolean.TRUE.equals(totp.getActivado())) {
            throw new TotpNoActivadoException();
        }
        // Borrar el row completo: limpia secret, scratch codes, intentos; deja rastro solo
        // en logs. Si el usuario quiere volver, hace setup nuevo.
        usuarioTotpRepository.delete(totp);
        log.info("2FA desactivado para correo={}", correo);
    }

    // ======================================================================
    // Validacion (consumida por /auth/2fa/verificar en Tanda 4)
    // ======================================================================

    @Override
    @Transactional
    public boolean validarCodigo(String correo, String codigo) {
        UsuarioTotp totp = usuarioTotpRepository.findByUsuarioCorreo(correo)
                .orElseThrow(TotpNoConfiguradoException::new);
        if (!Boolean.TRUE.equals(totp.getActivado())) {
            throw new TotpNoActivadoException();
        }
        verificarBloqueo(totp);

        if (codigo != null && codigo.equals(totp.getUltimoCodigoUsado())) {
            // Replay dentro del mismo paso: lo tratamos como invalido sin aumentar intentos
            // (si el usuario tecleo el mismo codigo dos veces por error de UI).
            log.warn("Replay de codigo TOTP detectado correo={}", correo);
            return false;
        }

        String secret = cryptoService.descifrar(totp.getSecretCifrado());

        // 1) Codigo TOTP numerico
        if (codigo != null && codigo.matches("^[0-9]{6}$") && codigoTotpValido(secret, codigo)) {
            totp.setIntentosFallidos((short) 0);
            totp.setBloqueadoHasta(null);
            totp.setUltimoCodigoUsado(codigo);
            totp.setFechaUltimoUso(OffsetDateTime.now(clock));
            usuarioTotpRepository.save(totp);
            return true;
        }

        // 2) Scratch code (uso unico)
        if (codigo != null && codigo.length() == SCRATCH_CODE_LEN && consumirScratchCode(totp, codigo)) {
            totp.setIntentosFallidos((short) 0);
            totp.setBloqueadoHasta(null);
            totp.setFechaUltimoUso(OffsetDateTime.now(clock));
            usuarioTotpRepository.save(totp);
            log.info("Scratch code consumido correo={}", correo);
            return true;
        }

        registrarIntentoFallido(totp);
        return false;
    }

    // ======================================================================
    // Helpers
    // ======================================================================

    private Usuario buscarUsuario(String correo) {
        return usuarioRepository.findByCorreo(correo)
                .orElseThrow(UsuarioNoEncontradoException::new);
    }

    private GoogleAuthenticator nuevoAuthenticator() {
        GoogleAuthenticatorConfig cfg = new GoogleAuthenticatorConfig.GoogleAuthenticatorConfigBuilder()
                .setWindowSize(props.getVentanaPasos() * 2 + 1) // libreria toma total: actual +/- N pasos
                .setTimeStepSizeInMillis(TimeUnit.SECONDS.toMillis(30))
                .build();
        return new GoogleAuthenticator(cfg);
    }

    private boolean codigoTotpValido(String secret, String codigo) {
        try {
            int n = Integer.parseInt(codigo);
            return nuevoAuthenticator().authorize(secret, n);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private List<String> generarScratchCodes(int cantidad) {
        List<String> out = new ArrayList<>(cantidad);
        for (int i = 0; i < cantidad; i++) {
            StringBuilder sb = new StringBuilder(SCRATCH_CODE_LEN);
            for (int c = 0; c < SCRATCH_CODE_LEN; c++) {
                sb.append(SCRATCH_ALPHABET[random.nextInt(SCRATCH_ALPHABET.length)]);
            }
            out.add(sb.toString());
        }
        return out;
    }

    private boolean consumirScratchCode(UsuarioTotp totp, String codigo) {
        if (totp.getScratchCodes() == null || totp.getScratchCodes().isBlank()) return false;
        String[] partes = totp.getScratchCodes().split(",");
        boolean match = false;
        for (int i = 0; i < partes.length; i++) {
            String hash = partes[i];
            if (hash.startsWith(SCRATCH_USED_PREFIX)) continue;
            if (passwordEncoder.matches(codigo, hash)) {
                partes[i] = SCRATCH_USED_PREFIX + hash;
                match = true;
                break;
            }
        }
        if (match) {
            totp.setScratchCodes(String.join(",", Arrays.asList(partes)));
        }
        return match;
    }

    private void verificarBloqueo(UsuarioTotp totp) {
        OffsetDateTime hasta = totp.getBloqueadoHasta();
        if (hasta != null && hasta.isAfter(OffsetDateTime.now(clock))) {
            throw new TotpBloqueadoException();
        }
    }

    private void registrarIntentoFallido(UsuarioTotp totp) {
        short actual = totp.getIntentosFallidos() == null ? 0 : totp.getIntentosFallidos();
        short nuevos = (short) (actual + 1);
        totp.setIntentosFallidos(nuevos);
        if (nuevos >= props.getMaxIntentos()) {
            totp.setBloqueadoHasta(OffsetDateTime.now(clock).plusMinutes(props.getBloqueoMinutos()));
            log.warn("2FA bloqueado por {} min tras {} intentos fallidos usuarioId={}",
                    props.getBloqueoMinutos(), nuevos, totp.getUsuarioId());
        }
        usuarioTotpRepository.save(totp);
    }

    private String construirOtpauthUri(String issuer, String label, String secret) {
        // Formato RFC 6238:  otpauth://totp/Issuer:label?secret=...&issuer=...
        String issuerEnc = URLEncoder.encode(issuer, StandardCharsets.UTF_8);
        String labelEnc  = URLEncoder.encode(label, StandardCharsets.UTF_8);
        return "otpauth://totp/" + issuerEnc + ":" + labelEnc
                + "?secret=" + secret + "&issuer=" + issuerEnc
                + "&algorithm=SHA1&digits=6&period=30";
    }

    private String generarQrDataUri(String otpauthUri) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(otpauthUri, BarcodeFormat.QR_CODE, 300, 300);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", bos);
            String b64 = Base64.getEncoder().encodeToString(bos.toByteArray());
            return "data:image/png;base64," + b64;
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo generar el QR TOTP", e);
        }
    }
}
