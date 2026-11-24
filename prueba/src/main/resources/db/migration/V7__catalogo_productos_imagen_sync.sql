-- ============================================================================
-- Fase 2A: Catalogo de productos con sincronizacion desde GestoPago
-- ============================================================================
-- Agrega URL de imagen y marca temporal de la ultima sincronizacion para cada
-- producto. El CatalogoSyncScheduler consulta GestoPago (getProductList.do)
-- una vez al dia (cron 6am MX) y hace upsert por (id_servicio, id_producto),
-- marcando como inactivos los productos que ya no vienen en la respuesta.
-- ============================================================================

ALTER TABLE catalogo_productos
    ADD COLUMN imagen_url           VARCHAR(500),
    ADD COLUMN fecha_sincronizacion TIMESTAMPTZ;

CREATE INDEX idx_catalogo_sync_fecha
    ON catalogo_productos (fecha_sincronizacion);

COMMENT ON COLUMN catalogo_productos.imagen_url           IS 'URL opcional de la imagen del producto (puede ser null)';
COMMENT ON COLUMN catalogo_productos.fecha_sincronizacion IS 'Ultima vez que el scheduler lo confirmo desde GestoPago';
