package com.proyecto.servicios.onboarding.security.ratelimit;

import com.proyecto.servicios.onboarding.entity.Usuario;
import com.proyecto.servicios.onboarding.exception.CuentaBloqueadaException;
import com.proyecto.servicios.onboarding.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * Control de bloqueo de cuenta por intentos fallidos de autenticacion.
 *
 * - verificarNoBloqueado: READ_ONLY (no muta), usado antes de validar password.
 * - registrarFallo: REQUIRES_NEW para que el incremento persista aun cuando la
 *   tx principal del login lance CredencialesInvalidas y haga rollback.
 * - registrarExito: REQUIRED, participa en la tx del login.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LockoutServiceImpl implements LockoutService {

    private final UsuarioRepository usuarioRepository;
    private final LockoutProperties props;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public void verificarNoBloqueado(String correo) {
        if (!props.isEnabled()) return;
        usuarioRepository.findByCorreo(correo).ifPresent(u -> {
            if (u.getBloqueadoHasta() != null
                    && u.getBloqueadoHasta().isAfter(OffsetDateTime.now(clock))) {
                log.warn("Login denegado: cuenta bloqueada hasta {} correo={}",
                        u.getBloqueadoHasta(), correo);
                throw new CuentaBloqueadaException();
            }
        });
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarFallo(String correo) {
        if (!props.isEnabled()) return;
        usuarioRepository.findByCorreo(correo).ifPresent(u -> {
            short actual = u.getIntentosFallidos() == null ? 0 : u.getIntentosFallidos();
            short nuevos = (short) (actual + 1);
            u.setIntentosFallidos(nuevos);
            if (nuevos >= props.getMaxIntentos()) {
                u.setBloqueadoHasta(OffsetDateTime.now(clock).plusMinutes(props.getDuracionMinutos()));
                u.setIntentosFallidos((short) 0);
                log.warn("Cuenta bloqueada por {} min tras {} intentos fallidos correo={}",
                        props.getDuracionMinutos(), nuevos, correo);
            }
            usuarioRepository.save(u);
        });
    }

    @Override
    @Transactional
    public void registrarExito(String correo) {
        if (!props.isEnabled()) return;
        usuarioRepository.findByCorreo(correo).ifPresent(u -> {
            boolean habiaIntentos = u.getIntentosFallidos() != null && u.getIntentosFallidos() > 0;
            boolean habiaBloqueo  = u.getBloqueadoHasta() != null;
            if (habiaIntentos || habiaBloqueo) {
                u.setIntentosFallidos((short) 0);
                u.setBloqueadoHasta(null);
                usuarioRepository.save(u);
            }
        });
    }
}
