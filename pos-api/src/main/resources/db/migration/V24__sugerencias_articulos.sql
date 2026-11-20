-- El nombre admite la misma longitud que la descripción del detalle de venta.
ALTER TABLE articulos ALTER COLUMN nombre TYPE VARCHAR(200);
CREATE INDEX idx_articulos_precio_activos ON articulos (precio_base) WHERE activo = TRUE;
CREATE INDEX idx_articulos_nombre_normalizado
    ON articulos (lower(regexp_replace(trim(nombre), '[[:space:]]+', ' ', 'g')));
