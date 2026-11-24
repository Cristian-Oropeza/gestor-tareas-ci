package com.proyecto.servicios.onboarding.security.sesion;

import com.proyecto.servicios.onboarding.repository.SesionActivaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Marca como inactivas las sesiones cuya ultima_actividad quedo fuera de la
 * ventana de inactividad (por defecto 30 min). El cron se configura en
 * app.sesion.cleanup-cron (por defecto cada 5 minutos).
 *
 * Ademas, al arrancar el backend ejecuta una limpieza inmediata: evita que
 * sesiones quedadas de un reinicio largo aparezcan como duplicadas al usuario
 * hasta que llegue la primera corrida del cron.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SesionCleanupScheduler {

    private final SesionActivaRepository repo;
    private final SesionProperties props;
    private final Clock clock;

    @Scheduled(cron = "${app.sesion.cleanup-cron:0 */5 * * * *}")
    @Transactional
    public void marcarSesionesInactivas() {
        OffsetDateTime ahora = OffsetDateTime.now(clock);
        OffsetDateTime corte = ahora.minusMinutes(props.getInactividadMinutos());
        int cerradas = repo.marcarInactivas(corte, ahora);
        if (cerradas > 0) {
            log.info("Cleanup de sesiones: {} marcadas como EXPIRADA_POR_INACTIVIDAD (corte={})", cerradas, corte);
        }
    }

    /** Al arrancar, limpiar sesiones viejas de una sola. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void cleanupAlStartup() {
        OffsetDateTime ahora = OffsetDateTime.now(clock);
        OffsetDateTime corte = ahora.minusMinutes(props.getInactividadMinutos());
        int cerradas = repo.marcarInactivas(corte, ahora);
        log.info("Startup: cleanup inicial de sesiones. {} marcadas inactivas (ventana {} min).",
                cerradas, props.getInactividadMinutos());
    }
}
