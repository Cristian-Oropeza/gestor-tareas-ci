package com.proyecto.servicios.onboarding.repository;

import com.proyecto.servicios.onboarding.entity.SesionActiva;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SesionActivaRepository extends JpaRepository<SesionActiva, Long> {

    Optional<SesionActiva> findByJtiAndActivaTrue(String jti);

    List<SesionActiva> findByClienteIdAndActivaTrueOrderByFechaCreacionDesc(Long clienteId);

    boolean existsByClienteIdAndActivaTrue(Long clienteId);

    @Modifying
    @Query("UPDATE SesionActiva s SET s.activa = false, s.motivoCierre = :motivo, "
         + "s.fechaCierre = :ahora WHERE s.clienteId = :clienteId AND s.activa = true")
    int cerrarPorCliente(@Param("clienteId") Long clienteId,
                         @Param("motivo") String motivo,
                         @Param("ahora") OffsetDateTime ahora);

    @Modifying
    @Query("UPDATE SesionActiva s SET s.activa = false, "
         + "s.motivoCierre = 'EXPIRADA_POR_INACTIVIDAD', s.fechaCierre = :ahora "
         + "WHERE s.activa = true AND s.ultimaActividad < :corte")
    int marcarInactivas(@Param("corte") OffsetDateTime corte,
                        @Param("ahora") OffsetDateTime ahora);

    @Modifying
    @Query("UPDATE SesionActiva s SET s.ultimaActividad = :ahora "
         + "WHERE s.jti = :jti AND s.activa = true")
    int tocarActividad(@Param("jti") String jti, @Param("ahora") OffsetDateTime ahora);
}
