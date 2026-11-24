package com.proyecto.servicios.onboarding.security.totp;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cifra y descifra el secret TOTP con AES-256 en modo GCM (autenticado).
 *
 * Formato persistido:  base64( IV(12B) || CIPHERTEXT || TAG(16B) )
 *
 * La llave viene de app.security.totp.encryption-key (env TOTP_SECRET_ENCRYPTION_KEY).
 * Base64 de 32 bytes = 44 chars. Sin esa llave el bean falla en arranque para
 * evitar persistir secrets 2FA sin cifrado real.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TotpCryptoService {

    private static final String ALG = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32; // AES-256

    private final TotpProperties props;
    private final SecureRandom random = new SecureRandom();

    private SecretKey key;

    @PostConstruct
    void init() {
        String raw = props.getEncryptionKey();
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException(
                    "TOTP_SECRET_ENCRYPTION_KEY no configurada. Genera 32 bytes base64 " +
                    "(openssl rand -base64 32) y ponla en la variable de entorno.");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(raw);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "TOTP_SECRET_ENCRYPTION_KEY no es base64 valido.", e);
        }
        if (bytes.length != KEY_BYTES) {
            throw new IllegalStateException(
                    "TOTP_SECRET_ENCRYPTION_KEY debe tener exactamente 32 bytes decodificados " +
                    "(AES-256). Recibido: " + bytes.length + " bytes.");
        }
        this.key = new SecretKeySpec(bytes, ALG);
        log.info("TotpCryptoService listo (AES-256-GCM, llave {} bytes).", KEY_BYTES);
    }

    /** Cifra el secret base32 del TOTP y devuelve base64 (IV || ciphertext || tag). */
    public String cifrar(String plaintext) {
        if (plaintext == null) {
            throw new IllegalArgumentException("plaintext no puede ser null");
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buf = ByteBuffer.allocate(iv.length + ct.length);
            buf.put(iv).put(ct);
            return Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception e) {
            throw new IllegalStateException("Error cifrando secret TOTP", e);
        }
    }

    /** Descifra el formato producido por cifrar(). */
    public String descifrar(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalArgumentException("encoded no puede ser vacio");
        }
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            if (all.length <= IV_BYTES) {
                throw new IllegalArgumentException("payload cifrado demasiado corto");
            }
            byte[] iv = new byte[IV_BYTES];
            byte[] ct = new byte[all.length - IV_BYTES];
            System.arraycopy(all, 0, iv, 0, IV_BYTES);
            System.arraycopy(all, IV_BYTES, ct, 0, ct.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] pt = cipher.doFinal(ct);
            return new String(pt, StandardCharsets.UTF_8);
        } catch (Exception e) {
            // Mensaje intencionalmente opaco: no revelar si la llave esta mal o el tag fallo.
            throw new IllegalStateException("Error descifrando secret TOTP", e);
        }
    }
}
