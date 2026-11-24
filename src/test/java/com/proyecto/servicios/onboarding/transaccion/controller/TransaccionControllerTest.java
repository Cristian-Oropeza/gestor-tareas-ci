package com.proyecto.servicios.onboarding.transaccion.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyecto.servicios.onboarding.exception.GlobalExceptionHandler;
import com.proyecto.servicios.onboarding.transaccion.dto.request.TransaccionConsultaRequest;
import com.proyecto.servicios.onboarding.transaccion.dto.request.TransaccionEjecutarRequest;
import com.proyecto.servicios.onboarding.transaccion.dto.response.TransaccionResponse;
import com.proyecto.servicios.onboarding.transaccion.entity.enums.EstadoTransaccion;
import com.proyecto.servicios.onboarding.transaccion.exception.ProveedorNoDisponibleException;
import com.proyecto.servicios.onboarding.transaccion.exception.TransaccionNoEncontradaException;
import com.proyecto.servicios.onboarding.transaccion.service.TransaccionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests del TransaccionController con MockMvc standalone. Verifica los mapeos HTTP:
 *   - 200 APLICADA
 *   - 202 EN_CONFIRMACION
 *   - 422 RECHAZADA
 *   - 503 NO_ENVIADA (via service)
 *   - 400 validacion de body
 *   - 404 transaccion no encontrada en /consulta
 */
class TransaccionControllerTest {

    private MockMvc mockMvc;
    private TransaccionService service;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setup() {
        service = Mockito.mock(TransaccionService.class);
        com.proyecto.servicios.onboarding.security.JwtService jwtService = org.mockito.Mockito.mock(com.proyecto.servicios.onboarding.security.JwtService.class);
        TransaccionController controller = new TransaccionController(service, jwtService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void POST_ejecutar_devuelve200_cuandoAPLICADA() throws Exception {
        when(service.ejecutar(any())).thenReturn(TransaccionResponse.builder()
                .id(1L).estado(EstadoTransaccion.APLICADA).build());

        mockMvc.perform(post("/api/v1/transacciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req("TELCEL", "TELCEL_100", "100.00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APLICADA"));
    }

    @Test
    void POST_ejecutar_devuelve202_cuandoENCONFIRMACION() throws Exception {
        when(service.ejecutar(any())).thenReturn(TransaccionResponse.builder()
                .id(1L).estado(EstadoTransaccion.EN_CONFIRMACION).build());

        mockMvc.perform(post("/api/v1/transacciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req("TELCEL", "TELCEL_100", "100.00"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.estado").value("EN_CONFIRMACION"));
    }

    @Test
    void POST_ejecutar_devuelve422_cuandoRECHAZADA() throws Exception {
        when(service.ejecutar(any())).thenReturn(TransaccionResponse.builder()
                .id(1L).estado(EstadoTransaccion.RECHAZADA).codigoProveedor("44").build());

        mockMvc.perform(post("/api/v1/transacciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req("TELCEL", "TELCEL_100", "100.00"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.estado").value("RECHAZADA"))
                .andExpect(jsonPath("$.codigoProveedor").value("44"));
    }

    @Test
    void POST_ejecutar_devuelve400_cuandoBodyInvalido() throws Exception {
        TransaccionEjecutarRequest incompleto = new TransaccionEjecutarRequest(); // todo nulo

        mockMvc.perform(post("/api/v1/transacciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(incompleto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value(400))
                .andExpect(jsonPath("$.mensaje", containsString("invalidos")));
    }

    @Test
    void POST_ejecutar_devuelve503_cuandoProveedorNoDisponible() throws Exception {
        when(service.ejecutar(any())).thenThrow(new ProveedorNoDisponibleException("modulo off"));

        mockMvc.perform(post("/api/v1/transacciones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req("TELCEL", "TELCEL_100", "100.00"))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value(503));
    }

    @Test
    void POST_consulta_devuelveTransaccionExistente() throws Exception {
        when(service.consultar(any())).thenReturn(TransaccionResponse.builder()
                .id(1L).referenciaOperacion("OP-XYZ").estado(EstadoTransaccion.APLICADA).build());

        TransaccionConsultaRequest cr = new TransaccionConsultaRequest();
        cr.setReferenciaOperacion("OP-XYZ");
        mockMvc.perform(post("/api/v1/transacciones/consulta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(cr)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.referenciaOperacion").value("OP-XYZ"));
    }

    @Test
    void POST_consulta_devuelve404_cuandoNoExiste() throws Exception {
        when(service.consultar(any())).thenThrow(new TransaccionNoEncontradaException("OP-X"));

        TransaccionConsultaRequest cr = new TransaccionConsultaRequest();
        cr.setReferenciaOperacion("OP-NOPE");
        mockMvc.perform(post("/api/v1/transacciones/consulta")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(cr)))
                .andExpect(status().isNotFound());
    }

    // ---------- helper ----------
    private TransaccionEjecutarRequest req(String srv, String prod, String monto) {
        TransaccionEjecutarRequest r = new TransaccionEjecutarRequest();
        r.setClienteId(1L);
        r.setIdempotencyKey("key-tests-00000001");
        r.setNumeroCuenta("014180747386759459");
        r.setIdServicio(srv);
        r.setIdProducto(prod);
        r.setMonto(new BigDecimal(monto));
        r.setTelefono("5512345678");
        return r;
    }
}
