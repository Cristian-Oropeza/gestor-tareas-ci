package com.proyecto.servicios.onboarding.transaccion.mapper;

import com.proyecto.servicios.onboarding.entity.Cliente;
import com.proyecto.servicios.onboarding.entity.Cuenta;
import com.proyecto.servicios.onboarding.transaccion.dto.response.TransaccionResponse;
import com.proyecto.servicios.onboarding.transaccion.entity.Transaccion;
import com.proyecto.servicios.onboarding.transaccion.entity.enums.EstadoTransaccion;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class TransaccionMapperTest {

    private final TransaccionMapper mapper = Mappers.getMapper(TransaccionMapper.class);

    @Test
    void toResponse_mapeaCamposBasicosYIdsDeRelaciones() {
        Cliente cliente = Cliente.builder().id(42L).build();
        Cuenta cuenta = Cuenta.builder().id(7L).build();
        Transaccion t = Transaccion.builder()
                .id(1L)
                .referenciaOperacion("OP-ABCDEF123")
                .idempotencyKey("op-key-1")
                .cliente(cliente)
                .cuenta(cuenta)
                .idServicio("TELCEL")
                .idProducto("TELCEL_100")
                .monto(new BigDecimal("100.00"))
                .moneda("MXN")
                .estado(EstadoTransaccion.APLICADA)
                .upc("ABC1234567")
                .codigoProveedor("01")
                .textoProveedor("Operacion exitosa")
                .numAutorizacion(123L)
                .build();

        TransaccionResponse r = mapper.toResponse(t);

        assertThat(r.getId()).isEqualTo(1L);
        assertThat(r.getClienteId()).isEqualTo(42L);
        assertThat(r.getCuentaId()).isEqualTo(7L);
        assertThat(r.getReferenciaOperacion()).isEqualTo("OP-ABCDEF123");
        assertThat(r.getEstado()).isEqualTo(EstadoTransaccion.APLICADA);
        assertThat(r.getCodigoProveedor()).isEqualTo("01");
        assertThat(r.getNumAutorizacion()).isEqualTo(123L);
    }
}
