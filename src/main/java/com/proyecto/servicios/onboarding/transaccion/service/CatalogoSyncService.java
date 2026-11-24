package com.proyecto.servicios.onboarding.transaccion.service;

public interface CatalogoSyncService {
    /** Dispara una sincronizacion con GestoPago. Devuelve cuantos productos quedaron activos. */
    int sincronizar();
}
