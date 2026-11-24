package com.proyecto.servicios.onboarding.transaccion.service.impl;

import com.proyecto.servicios.onboarding.transaccion.client.CatalogoXmlParser;
import com.proyecto.servicios.onboarding.transaccion.client.GestopagoCatalogoClient;
import com.proyecto.servicios.onboarding.transaccion.entity.CatalogoProducto;
import com.proyecto.servicios.onboarding.transaccion.repository.CatalogoProductoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogoSyncServiceImplTest {

    @Mock GestopagoCatalogoClient client;
    @Mock CatalogoXmlParser parser;
    @Mock CatalogoProductoRepository repo;
    Clock clock;
    CatalogoSyncServiceImpl service;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-11-24T06:00:00Z"), ZoneOffset.UTC);
        service = new CatalogoSyncServiceImpl(client, parser, repo, clock);
    }

    @Test
    void sincronizar_sinXml_devuelveCeroYNoTocaBd() {
        when(client.obtenerCatalogoXml()).thenReturn(Optional.empty());
        assertThat(service.sincronizar()).isZero();
        verifyNoInteractions(repo);
    }

    @Test
    void sincronizar_xmlVacio_noTocaBd() {
        when(client.obtenerCatalogoXml()).thenReturn(Optional.of("<RESPONSE></RESPONSE>"));
        when(parser.parsear(anyString())).thenReturn(List.of());
        assertThat(service.sincronizar()).isZero();
        verifyNoInteractions(repo);
    }

    @Test
    void sincronizar_upsertYMarcadoObsoletos_ok() {
        CatalogoXmlParser.ProductoXml a = new CatalogoXmlParser.ProductoXml();
        a.setIdServicio("TELCEL");
        a.setIdProducto("TELCEL_100");
        a.setDescripcion("Recarga Telcel 100");
        a.setTipoFront((short) 1);
        a.setPrecio(new BigDecimal("100.00"));
        a.setMontoMin(new BigDecimal("100.00"));
        a.setMontoMax(new BigDecimal("100.00"));

        when(client.obtenerCatalogoXml()).thenReturn(Optional.of("xml"));
        when(parser.parsear(anyString())).thenReturn(List.of(a));
        when(repo.findByIdServicioAndIdProducto("TELCEL", "TELCEL_100")).thenReturn(Optional.empty());
        when(repo.findByFechaSincronizacionBeforeOrFechaSincronizacionIsNull(any())).thenReturn(List.of());

        int n = service.sincronizar();

        assertThat(n).isEqualTo(1);
        verify(repo).save(any(CatalogoProducto.class));
    }

    @Test
    void sincronizar_productoSinIds_loIgnora() {
        CatalogoXmlParser.ProductoXml incompleto = new CatalogoXmlParser.ProductoXml();
        // sin idServicio ni idProducto -> debe ignorarse
        when(client.obtenerCatalogoXml()).thenReturn(Optional.of("xml"));
        when(parser.parsear(anyString())).thenReturn(List.of(incompleto));
        when(repo.findByFechaSincronizacionBeforeOrFechaSincronizacionIsNull(any())).thenReturn(List.of());

        assertThat(service.sincronizar()).isZero();
        verify(repo, never()).save(any());
    }
}
