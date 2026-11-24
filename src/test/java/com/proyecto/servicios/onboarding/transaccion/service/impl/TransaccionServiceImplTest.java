package com.proyecto.servicios.onboarding.transaccion.service.impl;

import java.time.OffsetDateTime;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.PageImpl;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import com.proyecto.servicios.onboarding.entity.Cliente;
import com.proyecto.servicios.onboarding.entity.Cuenta;
import com.proyecto.servicios.onboarding.entity.enums.EstatusCuenta;
import com.proyecto.servicios.onboarding.exception.ClienteNoEncontradoException;
import com.proyecto.servicios.onboarding.exception.CuentaNoEncontradaException;
import com.proyecto.servicios.onboarding.repository.ClienteRepository;
import com.proyecto.servicios.onboarding.repository.CuentaRepository;
import com.proyecto.servicios.onboarding.transaccion.client.GestopagoTxGateway;
import com.proyecto.servicios.onboarding.transaccion.client.dto.ResultadoTx;
import com.proyecto.servicios.onboarding.transaccion.client.xml.AutorizacionXml;
import com.proyecto.servicios.onboarding.transaccion.config.GestopagoProperties;
import com.proyecto.servicios.onboarding.transaccion.dto.request.TransaccionConsultaRequest;
import com.proyecto.servicios.onboarding.transaccion.dto.request.TransaccionEjecutarRequest;
import com.proyecto.servicios.onboarding.transaccion.dto.response.TransaccionResponse;
import com.proyecto.servicios.onboarding.transaccion.entity.CatalogoProducto;
import com.proyecto.servicios.onboarding.transaccion.entity.Transaccion;
import com.proyecto.servicios.onboarding.transaccion.entity.enums.EstadoTransaccion;
import com.proyecto.servicios.onboarding.transaccion.exception.CuentaNoActivaException;
import com.proyecto.servicios.onboarding.transaccion.exception.CuentaNoPerteneceAlClienteException;
import com.proyecto.servicios.onboarding.transaccion.exception.LimiteExcedidoException;
import com.proyecto.servicios.onboarding.transaccion.exception.MontoFueraDeRangoException;
import com.proyecto.servicios.onboarding.transaccion.exception.ProveedorNoDisponibleException;
import com.proyecto.servicios.onboarding.transaccion.exception.ReglaProductoViolatedException;
import com.proyecto.servicios.onboarding.transaccion.exception.TransaccionDuplicadaException;
import com.proyecto.servicios.onboarding.transaccion.exception.TransaccionNoEncontradaException;
import com.proyecto.servicios.onboarding.transaccion.mapper.TransaccionMapper;
import com.proyecto.servicios.onboarding.transaccion.repository.TransaccionRepository;
import com.proyecto.servicios.onboarding.transaccion.service.CatalogoProductoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios del orquestador TransaccionServiceImpl. Cubre:
 *   - Idempotencia (misma key => misma tx sin tocar al proveedor)
 *   - 403 CuentaNoPerteneceAlCliente
 *   - 422 CuentaNoActiva, MontoFueraDeRango, Limite diario, Regla producto
 *   - 409 Duplicado por ventana anti-dup
 *   - 503 modulo deshabilitado
 *   - Flujo feliz: valida => reserva => envia => aplica
 *   - Consulta por referencia_operacion
 */
@ExtendWith(MockitoExtension.class)
class TransaccionServiceImplTest {

    @Mock ClienteRepository clienteRepository;
    @Mock CuentaRepository cuentaRepository;
    @Mock TransaccionRepository transaccionRepository;
    @Mock CatalogoProductoService catalogoService;
    @Mock GestopagoTxGateway gateway;
    @Mock TransaccionMapper mapper;
    @Mock TransaccionTxOperations tx;

    private TransaccionServiceImpl service;
    private GestopagoProperties props;

    private Cliente cliente;
    private Cuenta  cuenta;
    private CatalogoProducto productoRecarga;

    @BeforeEach
    void setup() {
        props = new GestopagoProperties();
        props.setEnabled(true);
        props.getTx().setUnidad("083");
        props.getTx().setZonaHoraria("America/Mexico_City");
        props.getTx().setMontoMaximo(new BigDecimal("5000.00"));
        props.getTx().setTopeDiario(new BigDecimal("20000.00"));
        props.getTx().setVentanaAntiduplicadoSeg(30);

        Clock clock = Clock.fixed(Instant.parse("2026-11-03T10:00:00Z"), ZoneId.of("America/Mexico_City"));

        service = new TransaccionServiceImpl(clienteRepository, cuentaRepository, transaccionRepository,
                catalogoService, gateway, props, mapper, tx, clock);

        cliente = Cliente.builder().id(1L).build();
        cuenta  = Cuenta.builder().id(5L).cliente(cliente).estatus(EstatusCuenta.ACTIVA)
                .numeroCuenta("014180747386759459").saldo(new BigDecimal("1000.00")).build();

        productoRecarga = CatalogoProducto.builder()
                .idServicio("TELCEL").idProducto("TELCEL_100")
                .tipoFront((short) 1).idCatTipoServicio((short) 1)
                .precio(new BigDecimal("100.00"))
                .montoMin(new BigDecimal("100.00")).montoMax(new BigDecimal("100.00"))
                .moneda("MXN").activo(true).build();
    }

    @Test
    void ejecutar_lanza503_cuandoModuloDeshabilitado() {
        props.setEnabled(false);
        assertThatThrownBy(() -> service.ejecutar(req("TELCEL", "TELCEL_100", "100.00", "5512345678", null)))
                .isInstanceOf(ProveedorNoDisponibleException.class);
        verifyNoInteractions(gateway, tx);
    }

    @Test
    void ejecutar_idempotencia_devuelvePrevioSinLlamarAlProveedor() {
        Transaccion previa = Transaccion.builder().id(42L).referenciaOperacion("OP-OLD").build();
        when(transaccionRepository.findByIdempotencyKey("k-1")).thenReturn(Optional.of(previa));
        when(mapper.toResponse(previa)).thenReturn(
                TransaccionResponse.builder().id(42L).referenciaOperacion("OP-OLD").build());

        TransaccionEjecutarRequest r = req("TELCEL", "TELCEL_100", "100.00", "5512345678", null);
        r.setIdempotencyKey("k-1");

        TransaccionResponse res = service.ejecutar(r);

        assertThat(res.getId()).isEqualTo(42L);
        verifyNoInteractions(gateway, tx);
    }

    @Test
    void ejecutar_lanzaClienteNoEncontrado() {
        when(transaccionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(clienteRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ejecutar(req("TELCEL", "TELCEL_100", "100.00", "5512345678", null)))
                .isInstanceOf(ClienteNoEncontradoException.class);
    }

    @Test
    void ejecutar_lanzaCuentaNoEncontrada() {
        when(transaccionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(cuentaRepository.findByNumeroCuenta(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ejecutar(req("TELCEL", "TELCEL_100", "100.00", "5512345678", null)))
                .isInstanceOf(CuentaNoEncontradaException.class);
    }

    @Test
    void ejecutar_lanzaCuentaNoPerteneceAlCliente() {
        Cliente otroDueno = Cliente.builder().id(99L).build();
        cuenta.setCliente(otroDueno);
        when(transaccionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(cuentaRepository.findByNumeroCuenta(anyString())).thenReturn(Optional.of(cuenta));

        assertThatThrownBy(() -> service.ejecutar(req("TELCEL", "TELCEL_100", "100.00", "5512345678", null)))
                .isInstanceOf(CuentaNoPerteneceAlClienteException.class);
    }

    @Test
    void ejecutar_lanzaCuentaNoActiva() {
        cuenta.setEstatus(EstatusCuenta.BLOQUEADA);
        when(transaccionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(cuentaRepository.findByNumeroCuenta(anyString())).thenReturn(Optional.of(cuenta));

        assertThatThrownBy(() -> service.ejecutar(req("TELCEL", "TELCEL_100", "100.00", "5512345678", null)))
                .isInstanceOf(CuentaNoActivaException.class);
    }

    @Test
    void ejecutar_lanzaMontoFueraDeRango() {
        prepararHasta(productoRecarga);

        // Monto 200 pero el rango es [100, 100]
        assertThatThrownBy(() -> service.ejecutar(req("TELCEL", "TELCEL_100", "200.00", "5512345678", null)))
                .isInstanceOf(MontoFueraDeRangoException.class);
    }

    @Test
    void ejecutar_lanzaReglaProducto_telefonoFaltanteEnRecarga() {
        CatalogoProducto noCanjeable = CatalogoProducto.builder()
                .idServicio("TELCEL").idProducto("TELCEL_100")
                .tipoFront((short) 1).idCatTipoServicio((short) 5) // no canjeable
                .precio(new BigDecimal("100.00"))
                .montoMin(new BigDecimal("100.00")).montoMax(new BigDecimal("100.00"))
                .moneda("MXN").activo(true).build();
        prepararHasta(noCanjeable);

        TransaccionEjecutarRequest r = req("TELCEL", "TELCEL_100", "100.00", null, null); // sin telefono
        assertThatThrownBy(() -> service.ejecutar(r))
                .isInstanceOf(ReglaProductoViolatedException.class);
    }

    @Test
    void ejecutar_lanzaReglaProducto_referenciaFaltanteEnPagoServicio() {
        CatalogoProducto cfe = CatalogoProducto.builder()
                .idServicio("CFE").idProducto("CFE_PAGO")
                .tipoFront((short) 2).idCatTipoServicio((short) 2)
                .precio(null).montoMin(new BigDecimal("10.00")).montoMax(new BigDecimal("50000.00"))
                .moneda("MXN").activo(true).build();
        when(transaccionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(cuentaRepository.findByNumeroCuenta(anyString())).thenReturn(Optional.of(cuenta));
        when(catalogoService.obtenerActivoOExcepcion("CFE", "CFE_PAGO")).thenReturn(cfe);

        TransaccionEjecutarRequest r = req("CFE", "CFE_PAGO", "500.00", null, null);
        assertThatThrownBy(() -> service.ejecutar(r))
                .isInstanceOf(ReglaProductoViolatedException.class);
    }

    @Test
    void ejecutar_lanzaLimiteExcedido_porMontoMaximo() {
        CatalogoProducto rangoAmplio = CatalogoProducto.builder()
                .idServicio("TELCEL").idProducto("TELCEL_100")
                .tipoFront((short) 1).idCatTipoServicio((short) 1)
                .precio(null).montoMin(new BigDecimal("1.00")).montoMax(new BigDecimal("10000.00"))
                .moneda("MXN").activo(true).build();
        prepararHasta(rangoAmplio);
        props.getTx().setMontoMaximo(new BigDecimal("500.00"));

        TransaccionEjecutarRequest r = req("TELCEL", "TELCEL_100", "1000.00", "5512345678", null);
        assertThatThrownBy(() -> service.ejecutar(r))
                .isInstanceOf(LimiteExcedidoException.class);
    }

    @Test
    void ejecutar_lanzaDuplicadaPorAntiDup() {
        prepararHasta(productoRecarga);
        when(transaccionRepository.sumarMontoPorCuentaDesde(eq(5L), anyList(), any()))
                .thenReturn(BigDecimal.ZERO);
        when(transaccionRepository.contarAntiDuplicado(eq(5L), eq("TELCEL_100"), any(), any(), anyList()))
                .thenReturn(1L); // ya hay una igual en la ventana

        assertThatThrownBy(() -> service.ejecutar(req("TELCEL", "TELCEL_100", "100.00", "5512345678", null)))
                .isInstanceOf(TransaccionDuplicadaException.class);
    }

    @Test
    void ejecutar_flujoFeliz_reservaYLlamaProveedorYAplica() {
        prepararHasta(productoRecarga);
        when(transaccionRepository.sumarMontoPorCuentaDesde(any(), anyList(), any())).thenReturn(BigDecimal.ZERO);
        when(transaccionRepository.contarAntiDuplicado(any(), any(), any(), any(), anyList())).thenReturn(0L);

        when(tx.apartarSaldoYCrearTransaccion(eq(cliente), eq(cuenta), any(), eq(productoRecarga), anyString(), anyString(), anyString(), eq("083")))
                .thenReturn(55L);

        AutorizacionXml xml = new AutorizacionXml();
        xml.setNumAutorizacion("1001");
        AutorizacionXml.Mensaje m = new AutorizacionXml.Mensaje();
        m.setCodigo("01"); m.setTexto("ok");
        xml.setMensaje(m);
        ResultadoTx exito = ResultadoTx.respuesta(xml);
        when(gateway.enviar(any())).thenReturn(exito);

        Transaccion finalizada = Transaccion.builder().id(55L).estado(EstadoTransaccion.APLICADA).build();
        when(tx.aplicarResultadoEnvio(55L, exito)).thenReturn(finalizada);
        when(mapper.toResponse(finalizada)).thenReturn(
                TransaccionResponse.builder().id(55L).estado(EstadoTransaccion.APLICADA).build());

        TransaccionResponse res = service.ejecutar(req("TELCEL", "TELCEL_100", "100.00", "5512345678", null));

        assertThat(res.getEstado()).isEqualTo(EstadoTransaccion.APLICADA);
        verify(tx).marcarEnviada(eq(55L), anyString(), anyString());
        verify(gateway).enviar(any());
        verify(tx).aplicarResultadoEnvio(55L, exito);
    }

    @Test
    void consultar_devuelveTxExistente() {
        Transaccion t = Transaccion.builder().id(10L).referenciaOperacion("OP-XYZ").build();
        when(transaccionRepository.findByReferenciaOperacion("OP-XYZ")).thenReturn(Optional.of(t));
        when(mapper.toResponse(t)).thenReturn(TransaccionResponse.builder().id(10L).build());

        TransaccionConsultaRequest cr = new TransaccionConsultaRequest();
        cr.setReferenciaOperacion("OP-XYZ");

        TransaccionResponse res = service.consultar(cr);
        assertThat(res.getId()).isEqualTo(10L);
    }

    @Test
    void consultar_lanzaNoEncontradaCuandoNoExiste() {
        when(transaccionRepository.findByReferenciaOperacion("OP-NOPE")).thenReturn(Optional.empty());
        TransaccionConsultaRequest cr = new TransaccionConsultaRequest();
        cr.setReferenciaOperacion("OP-NOPE");

        assertThatThrownBy(() -> service.consultar(cr))
                .isInstanceOf(TransaccionNoEncontradaException.class);
    }

    // ---------- helpers ----------
    private void prepararHasta(CatalogoProducto producto) {
        when(transaccionRepository.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(cuentaRepository.findByNumeroCuenta(anyString())).thenReturn(Optional.of(cuenta));
        when(catalogoService.obtenerActivoOExcepcion(producto.getIdServicio(), producto.getIdProducto()))
                .thenReturn(producto);
    }

    private TransaccionEjecutarRequest req(String srv, String prod, String monto, String tel, String ref) {
        TransaccionEjecutarRequest r = new TransaccionEjecutarRequest();
        r.setClienteId(1L);
        r.setIdempotencyKey("key-" + System.nanoTime());
        r.setNumeroCuenta("014180747386759459");
        r.setIdServicio(srv);
        r.setIdProducto(prod);
        r.setMonto(new BigDecimal(monto));
        r.setTelefono(tel);
        r.setReferencia(ref);
        return r;
    }


}
