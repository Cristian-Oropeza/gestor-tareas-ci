package com.proyecto.servicios.onboarding.repository;

import com.proyecto.servicios.onboarding.entity.UsuarioTotp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioTotpRepository extends JpaRepository<UsuarioTotp, Long> {
    Optional<UsuarioTotp> findByUsuarioCorreo(String correo);
    boolean existsByUsuarioIdAndActivadoTrue(Long usuarioId);
}
