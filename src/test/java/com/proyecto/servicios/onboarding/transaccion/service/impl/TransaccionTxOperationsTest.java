package com.proyecto.servicios.onboarding.transaccion.service.impl;

import com.proyecto.servicios.onboarding.entity.Cliente;
import com.proyecto.servicios.onboarding.entity.Cuenta;
import com.proyecto.servicios.onboarding.entity.enums.EstatusCuenta;
import com.proyecto.servicios.onboarding.repository.CuentaRepository;
import com.proyecto.servicios.onboarding.transaccion.client.dto.ResultadoTx;
import com.proyecto.servicios.onboarding.transaccion.client.xml.AutorizacionXml;
import com.proyecto.servicios.onboarding.transaccion.dto.request.TransaccionEjecutarRequest;
import com.proyecto.servicios.onboarding.transaccion.entity.CatalogoProducto;
import com.proyecto.servicios.onboarding.transaccion.entity.Transaccion;
import com.proyecto.servicios.onboarding.transaccion.entity.enums.EstadoTransaccion;
import com.proyecto.servicios.onboarding.transaccion.exception.CuentaNoActivaException;
import com.proyecto.servicios.onboarding.transaccion.exception.SaldoInsuficienteException;
import com.proyecto.servicios.onboarding.transaccion.repository.TransaccionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios de TransaccionTxOperations. Cubre:
 *   - Reserva de saldo con todas las validaciones.
 *   - Flujo APLICADA (saldo queda descontado).
 *   - Flujo RECHAZADA (saldo devuelto).
 *   - Flujo EN_CONFIRMACION (saldo NO devuelto).
 *   - Flujo NO_ENVIADA (saldo devuelto).
 *   - Conciliacion.
 */
@ExtendWith(MockitoExtension.class)
class TransaccionTxOperationsTest {

    @Mock CuentaRepository cuentaRepository;
    @Mock TransaccionRepository transaccionRepository;
    @InjectMocks TransaccionTxOperations ops;

    private Cliente cliente;
    private Cuenta cuenta;
    private CatalogoProducto producto;

    @BeforeEach
    void setup() {
        cliente = Cliente.builder().id(1L).build();
        cuenta  = Cuenta.builder().id(5L).cliente(cliente).estatus(EstatusCuenta.ACTIVA)
                .saldo(new BigDecimal("1000.00")).build();
        producto = CatalogoProducto.builder()
                .idServicio("TELCEL").idProducto("TELCEL_100").moneda("MXN").build();
    }

    @Test
    void apartarSaldo_debitaYCreaTransaccionPendiente() {
        when(cuentaRepository.findById(5L)).thenReturn(Optional.of(cuenta));
        when(transaccionRepository.save(any())).thenAnswer(inv -> {
            Transaccion t = inv.getArgument(0);
            t.setId(99L);
            return t;
        });

        TransaccionEjecutarRequest req = req("100.00");
        Long id = ops.apartarSaldoYCrearTransaccion(cliente, cuenta, req, producto, "OP-1", "UPC00001", "5512345678", "083");

        assertThat(id).isEqualTo(99L);
        assertThat(cuenta.getSaldo()).isEqualByComparingTo("900.00");

        ArgumentCaptor<Transaccion> cap = ArgumentCaptor.forClass(Transaccion.class);
        verify(transaccionRepository).save(cap.capture());
        Transaccion creada = cap.getValue();
        assertThat(creada.getEstado()).isEqualTo(EstadoTransaccion.PENDIENTE);
        assertThat(creada.getReferenciaOperacion()).isEqualTo("OP-1");
        assertThat(creada.getUpc()).isEqualTo("UPC00001");
        assertThat(creada.getTelefono()).isEqualTo("5512345678");
    }

    @Test
    void apartarSaldo_lanzaSaldoInsuficiente() {
        cuenta.setSaldo(new BigDecimal("50.00"));
        when(cuentaRepository.findById(5L)).thenReturn(Optional.of(cuenta));

        assertThatThrownBy(() -> ops.apartarSaldoYCrearTransaccion(
                cliente, cuenta, req("100.00"), producto, "OP-1", "UPC", "5512345678", "083"))
                .isInstanceOf(SaldoInsuficienteException.class);
        verify(transaccionRepository, never()).save(any());
    }

    @Test
    void apartarSaldo_lanzaCuentaNoActiva() {
        cuenta.setEstatus(EstatusCuenta.BLOQUEADA);
        when(cuentaRepository.findById(5L)).thenReturn(Optional.of(cuenta));

        assertThatThrownBy(() -> ops.apartarSaldoYCrearTransaccion(
                cliente, cuenta, req("100.00"), producto, "OP-1", "UPC", "5512345678", "083"))
                .isInstanceOf(CuentaNoActivaException.class);
    }

    @Test
    void aplicarResultadoEnvio_exitoso_dejaAPLICADA() {
        Transaccion t = baseTx();
        when(transaccionRepository.findById(10L)).thenReturn(Optional.of(t));
        when(transaccionRepository.save(any())).thenReturn(t);

        AutorizacionXml xml = xmlAprobada("01", 777L);
        Transaccion res = ops.aplicarResultadoEnvio(10L, ResultadoTx.respuesta(xml));

        assertThat(res.getEstado()).isEqualTo(EstadoTransaccion.APLICADA);
        assertThat(res.getCodigoProveedor()).isEqualTo("01");
        assertThat(res.getNumAutorizacion()).isEqualTo(777L);
        // Saldo NO se devolvio
        verify(cuentaRepository, never()).save(any());
    }

    @Test
    void aplicarResultadoEnvio_rechazado_dejaRECHAZADAyDevuelveSaldo() {
        Transaccion t = baseTx();
        when(transaccionRepository.findById(10L)).thenReturn(Optional.of(t));
        when(cuentaRepository.findById(5L)).thenReturn(Optional.of(cuenta));
        when(transaccionRepository.save(any())).thenReturn(t);

        AutorizacionXml xml = xmlRechazado("44", "Servicio no asignado");
        BigDecimal saldoAntes = cuenta.getSaldo();

        Transaccion res = ops.aplicarResultadoEnvio(10L, ResultadoTx.respuesta(xml));

        assertThat(res.getEstado()).isEqualTo(EstadoTransaccion.RECHAZADA);
        assertThat(res.getCodigoProveedor()).isEqualTo("44");
        assertThat(cuenta.getSaldo()).isEqualByComparingTo(saldoAntes.add(t.getMonto()));
        verify(cuentaRepository).save(cuenta);
    }

    @Test
    void aplicarResultadoEnvio_sinRespuesta_dejaENCONFIRMACIONsinDevolver() {
        Transaccion t = baseTx();
        when(transaccionRepository.findById(10L)).thenReturn(Optional.of(t));
        when(transaccionRepository.save(any())).thenReturn(t);

        Transaccion res = ops.aplicarResultadoEnvio(10L, ResultadoTx.sinRespuesta("Timeout"));

        assertThat(res.getEstado()).isEqualTo(EstadoTransaccion.EN_CONFIRMACION);
        verify(cuentaRepository, never()).save(any());
    }

    @Test
    void aplicarResultadoEnvio_noEnviada_dejaNOENVIADAyDevuelveSaldo() {
        Transaccion t = baseTx();
        when(transaccionRepository.findById(10L)).thenReturn(Optional.of(t));
        when(cuentaRepository.findById(5L)).thenReturn(Optional.of(cuenta));
        when(transaccionRepository.save(any())).thenReturn(t);

        Transaccion res = ops.aplicarResultadoEnvio(10L, ResultadoTx.noEnviada("sin token"));

        assertThat(res.getEstado()).isEqualTo(EstadoTransaccion.NO_ENVIADA);
        verify(cuentaRepository).save(cuenta);
    }

    @Test
    void aplicarResultadoConciliacion_siYaEsTerminal_noHaceNada() {
        Transaccion t = baseTx();
        t.setEstado(EstadoTransaccion.APLICADA);
        when(transaccionRepository.findById(10L)).thenReturn(Optional.of(t));

        Transaccion res = ops.aplicarResultadoConciliacion(10L, ResultadoTx.sinRespuesta("x"));

        assertThat(res.getEstado()).isEqualTo(EstadoTransaccion.APLICADA);
        verify(transaccionRepository, never()).save(any());
    }

    @Test
    void aplicarResultadoConciliacion_aprobadaConCodigo06_dejaAPLICADA() {
        Transaccion t = baseTx();
        t.setEstado(EstadoTransaccion.EN_CONFIRMACION);
        when(transaccionRepository.findById(10L)).thenReturn(Optional.of(t));
        when(transaccionRepository.save(any())).thenReturn(t);

        AutorizacionXml xml = xmlAprobada("06", 999L);
        Transaccion res = ops.aplicarResultadoConciliacion(10L, ResultadoTx.respuesta(xml));

        assertThat(res.getEstado()).isEqualTo(EstadoTransaccion.APLICADA);
        assertThat(res.getNumAutorizacion()).isEqualTo(999L);
    }

    @Test
    void incrementarIntentoConciliacion_sumaUno() {
        Transaccion t = baseTx();
        t.setIntentosConciliacion(3);
        when(transaccionRepository.findById(10L)).thenReturn(Optional.of(t));
        when(transaccionRepository.save(any())).thenReturn(t);

        ops.incrementarIntentoConciliacion(10L);

        assertThat(t.getIntentosConciliacion()).isEqualTo(4);
        verify(transaccionRepository).save(t);
    }

    // ---------- helpers ----------
    private TransaccionEjecutarRequest req(String monto) {
        TransaccionEjecutarRequest r = new TransaccionEjecutarRequest();
        r.setClienteId(1L);
        r.setIdempotencyKey("k-" + monto);
        r.setNumeroCuenta("014180747386759459");
        r.setIdServicio("TELCEL");
        r.setIdProducto("TELCEL_100");
        r.setMonto(new BigDecimal(monto));
        r.setTelefono("5512345678");
        return r;
    }

    private Transaccion baseTx() {
        return Transaccion.builder()
                .id(10L).cliente(cliente).cuenta(cuenta)
                .monto(new BigDecimal("100.00"))
                .estado(EstadoTransaccion.EN_CONFIRMACION)
                .idempotencyKey("k-100")
                .upc("UPCTEST0001")
                .build();
    }

    private AutorizacionXml xmlAprobada(String codigo, Long numAut) {
        AutorizacionXml x = new AutorizacionXml();
        x.setNumAutorizacion(String.valueOf(numAut));
        AutorizacionXml.Mensaje m = new AutorizacionXml.Mensaje();
        m.setCodigo(codigo);
        m.setTexto("Operacion exitosa");
        x.setMensaje(m);
        return x;
    }

    private AutorizacionXml xmlRechazado(String codigo, String texto) {
        AutorizacionXml x = new AutorizacionXml();
        x.setNumAutorizacion("-1");
        AutorizacionXml.Mensaje m = new AutorizacionXml.Mensaje();
        m.setCodigo(codigo);
        m.setTexto(texto);
        x.setMensaje(m);
        return x;
    }
}
