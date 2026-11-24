package com.proyecto.servicios.onboarding.security.sesion;

import com.proyecto.servicios.onboarding.repository.SesionActivaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Marca como inactivas las sesiones cuya ultima_actividad quedo fuera de la
 * ventana de inactividad (por defecto 30 min). El cron se configura en
 * app.sesion.cleanup-cron (por defecto cada 5 minutos).
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
}
