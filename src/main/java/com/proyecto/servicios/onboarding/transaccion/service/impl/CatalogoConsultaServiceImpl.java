package com.proyecto.servicios.onboarding.transaccion.service.impl;

import com.proyecto.servicios.onboarding.transaccion.dto.request.CatalogoConsultarRequest;
import com.proyecto.servicios.onboarding.transaccion.dto.response.CatalogoProductoResponse;
import com.proyecto.servicios.onboarding.transaccion.entity.CatalogoProducto;
import com.proyecto.servicios.onboarding.transaccion.repository.CatalogoProductoRepository;
import com.proyecto.servicios.onboarding.transaccion.service.CatalogoConsultaService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Consulta del catalogo con filtros dinamicos (JPA Specifications) y paginacion.
 * Entrega siempre el DTO, nunca la entidad, para no acoplar el frontend a JPA.
 */
@Service
@RequiredArgsConstructor
public class CatalogoConsultaServiceImpl implements CatalogoConsultaService {

    private final CatalogoProductoRepository repo;

    @Override
    @Transactional(readOnly = true)
    public Page<CatalogoProductoResponse> consultar(CatalogoConsultarRequest req) {
        Specification<CatalogoProducto> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (!Boolean.TRUE.equals(req.getIncluirInactivos())) {
                ps.add(cb.isTrue(root.get("activo")));
            }
            if (req.getIdServicio() != null && !req.getIdServicio().isBlank()) {
                ps.add(cb.equal(root.get("idServicio"), req.getIdServicio().trim()));
            }
            if (req.getDescripcion() != null && !req.getDescripcion().isBlank()) {
                ps.add(cb.like(cb.lower(root.get("descripcion")),
                        "%" + req.getDescripcion().toLowerCase().trim() + "%"));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };

        PageRequest pr = PageRequest.of(
                req.getPaginacion().getPage(),
                req.getPaginacion().getSize(),
                Sort.by(Sort.Direction.ASC, "idServicio", "idProducto"));

        return repo.findAll(spec, pr).map(this::toDto);
    }

    private CatalogoProductoResponse toDto(CatalogoProducto p) {
        return new CatalogoProductoResponse(
                p.getId(), p.getIdServicio(), p.getIdProducto(), p.getDescripcion(),
                p.getTipoFront(), p.getPrecio(), p.getMontoMin(), p.getMontoMax(),
                p.getMoneda(), p.getActivo(), p.getImagenUrl(), p.getFechaSincronizacion()
        );
    }
}
