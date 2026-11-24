package com.proyecto.servicios.onboarding.transaccion.scheduler;

import com.proyecto.servicios.onboarding.transaccion.config.GestopagoProperties;
import com.proyecto.servicios.onboarding.transaccion.service.CatalogoSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Corre la sincronizacion del catalogo con GestoPago una vez al dia (cron 6am MX
 * por default). Se puede deshabilitar con gestopago.catalogo.sync-enabled=false.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "gestopago", name = "enabled", havingValue = "true")
public class CatalogoSyncScheduler {

    private final CatalogoSyncService syncService;
    private final GestopagoProperties props;

    @Scheduled(cron = "${gestopago.catalogo.sync-cron:0 0 6 * * *}",
               zone = "${gestopago.catalogo.sync-zone:America/Mexico_City}")
    public void sincronizar() {
        if (!props.getCatalogo().isSyncEnabled()) {
            log.debug("Catalogo sync deshabilitado por config (gestopago.catalogo.sync-enabled=false)");
            return;
        }
        try {
            int n = syncService.sincronizar();
            log.info("CatalogoSyncScheduler: completo, {} productos", n);
        } catch (Exception ex) {
            log.error("CatalogoSyncScheduler: fallo inesperado - {}", ex.getMessage(), ex);
        }
    }
}
