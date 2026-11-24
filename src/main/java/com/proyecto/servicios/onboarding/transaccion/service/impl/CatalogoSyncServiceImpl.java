package com.proyecto.servicios.onboarding.transaccion.service.impl;

import com.proyecto.servicios.onboarding.transaccion.client.CatalogoXmlParser;
import com.proyecto.servicios.onboarding.transaccion.client.GestopagoCatalogoClient;
import com.proyecto.servicios.onboarding.transaccion.entity.CatalogoProducto;
import com.proyecto.servicios.onboarding.transaccion.repository.CatalogoProductoRepository;
import com.proyecto.servicios.onboarding.transaccion.service.CatalogoSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Orquesta la sincronizacion del catalogo local con GestoPago:
 *  1) Pide el XML a getProductList.do (si falla, no toca la BD).
 *  2) Parsea y filtra productos sin id.
 *  3) Upsert por (id_servicio, id_producto), seteando fecha_sincronizacion = NOW().
 *  4) Marca inactivos los productos locales cuya fecha quedo antes de esta corrida.
 *
 * La fecha se trunca a milisegundos para evitar que la condicion "fecha < ahora"
 * marque erroneamente los que acabamos de guardar.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogoSyncServiceImpl implements CatalogoSyncService {

    private final GestopagoCatalogoClient client;
    private final CatalogoXmlParser parser;
    private final CatalogoProductoRepository repo;
    private final Clock clock;

    @Override
    @Transactional
    public int sincronizar() {
        long start = System.currentTimeMillis();
        Optional<String> xml = client.obtenerCatalogoXml();
        if (xml.isEmpty()) {
            log.warn("Catalogo sync abortado: no se pudo obtener XML. BD local sin cambios.");
            return 0;
        }

        List<CatalogoXmlParser.ProductoXml> items = parser.parsear(xml.get());
        if (items.isEmpty()) {
            log.warn("Catalogo sync: GestoPago devolvio 0 productos validos. BD local sin cambios.");
            return 0;
        }

        OffsetDateTime ahora = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
        int upserts = 0;
        for (CatalogoXmlParser.ProductoXml it : items) {
            if (it.getIdServicio() == null || it.getIdProducto() == null) continue;
            upsert(it, ahora);
            upserts++;
        }

        // Marca inactivos los que no vinieron en esta corrida.
        List<CatalogoProducto> obsoletos = repo.findByFechaSincronizacionBeforeOrFechaSincronizacionIsNull(ahora)
                .stream().filter(p -> Boolean.TRUE.equals(p.getActivo())).toList();
        for (CatalogoProducto o : obsoletos) {
            // Preservamos los productos seed sembrados en V2 (que nunca tuvieron fechaSincronizacion)
            // solo si Gestopago no los menciono en esta corrida: los dejamos activos
            // pero marcados como no-sincronizados (para debug). Opcional: comentar lineas de abajo.
            if (o.getFechaSincronizacion() == null) continue;
            o.setActivo(Boolean.FALSE);
            repo.save(o);
        }

        long dur = System.currentTimeMillis() - start;
        log.info("Catalogo sync OK: {} productos upsert, {} marcados obsoletos, {} ms",
                upserts, obsoletos.stream().filter(p -> p.getFechaSincronizacion() != null).count(), dur);
        return upserts;
    }

    private void upsert(CatalogoXmlParser.ProductoXml it, OffsetDateTime ahora) {
        CatalogoProducto p = repo.findByIdServicioAndIdProducto(it.getIdServicio(), it.getIdProducto())
                .orElseGet(CatalogoProducto::new);
        p.setIdServicio(it.getIdServicio());
        p.setIdProducto(it.getIdProducto());
        if (it.getDescripcion() != null) p.setDescripcion(it.getDescripcion());
        if (it.getTipoFront() != null)   p.setTipoFront(it.getTipoFront());
        if (it.getIdCatTipoServicio() != null) p.setIdCatTipoServicio(it.getIdCatTipoServicio());
        if (it.getPrecio() != null)      p.setPrecio(it.getPrecio());
        if (it.getMontoMin() != null)    p.setMontoMin(it.getMontoMin());
        if (it.getMontoMax() != null)    p.setMontoMax(it.getMontoMax());
        if (it.getImagenUrl() != null)   p.setImagenUrl(it.getImagenUrl());
        if (p.getMoneda() == null)       p.setMoneda("MXN");
        if (p.getDescripcion() == null)  p.setDescripcion(it.getIdProducto());
        if (p.getTipoFront() == null)    p.setTipoFront((short) 1);
        if (p.getMontoMin() == null)     p.setMontoMin(it.getPrecio() != null ? it.getPrecio() : BigDecimal.ONE);
        if (p.getMontoMax() == null)     p.setMontoMax(it.getPrecio() != null ? it.getPrecio() : new BigDecimal("5000.00"));
        p.setActivo(Boolean.TRUE);
        p.setFechaSincronizacion(ahora);
        repo.save(p);
    }
}
