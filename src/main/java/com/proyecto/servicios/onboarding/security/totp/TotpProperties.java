package com.proyecto.servicios.onboarding.security.totp;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuracion 2FA TOTP. Mapea las propiedades app.security.totp.* del YAML.
 * Ver application.yml y .env.example.
 */
@Component
@ConfigurationProperties(prefix = "app.security.totp")
@Getter @Setter
public class TotpProperties {

    /** Nombre visible al escanear el QR en la app autenticadora. */
    private String issuer = "GestoPago Comercio PWA Oropeza";

    /** Llave base64 (32 bytes) para cifrar el secret TOTP en BD. OBLIGATORIO en prod. */
    private String encryptionKey = "";

    /** Numero de scratch codes de respaldo generados en el setup. */
    private int scratchCodesCount = 10;

    /** Intentos fallidos consecutivos antes de bloquear 2FA. */
    private int maxIntentos = 3;

    /** Duracion del bloqueo tras exceder maxIntentos (minutos). */
    private int bloqueoMinutos = 15;

    /**
     * Ventana de validacion TOTP en pasos de 30s.
     * 1 = acepta el codigo actual + 1 anterior + 1 siguiente (tolera +/- 30s de desync).
     */
    private int ventanaPasos = 1;
}
