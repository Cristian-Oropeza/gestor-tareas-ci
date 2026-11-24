package com.proyecto.servicios.onboarding.transaccion.service;

import com.proyecto.servicios.onboarding.transaccion.dto.request.CatalogoConsultarRequest;
import com.proyecto.servicios.onboarding.transaccion.dto.response.CatalogoProductoResponse;
import org.springframework.data.domain.Page;

public interface CatalogoConsultaService {
    Page<CatalogoProductoResponse> consultar(CatalogoConsultarRequest req);
}
