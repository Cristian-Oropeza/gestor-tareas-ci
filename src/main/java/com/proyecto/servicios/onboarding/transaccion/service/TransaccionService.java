package com.proyecto.servicios.onboarding.transaccion.service;

import com.proyecto.servicios.onboarding.transaccion.dto.request.TransaccionConsultaRequest;
import com.proyecto.servicios.onboarding.transaccion.dto.request.TransaccionEjecutarRequest;
import com.proyecto.servicios.onboarding.transaccion.dto.response.TransaccionResponse;

public interface TransaccionService {

    /** Ejecuta sendTx: valida, reserva saldo, envia a GestoPago y aplica/revierte segun resultado. */
    TransaccionResponse ejecutar(TransaccionEjecutarRequest req);

    /** Consulta el estado vivo de una transaccion por referencia_operacion (opaca, publica). */
    TransaccionResponse consultar(TransaccionConsultaRequest req);

    /** Historial del cliente (solo APLICADA). clienteId se pasa explicito desde el controller (viene del JWT). */
    org.springframework.data.domain.Page<com.proyecto.servicios.onboarding.transaccion.dto.response.MiCompraResponse>
            misCompras(Long clienteId,
                       com.proyecto.servicios.onboarding.transaccion.dto.request.MisComprasRequest req);
}
