ALTER TABLE cierres_diarios
ADD COLUMN monto_recibido_calculado NUMERIC(19, 2) NOT NULL DEFAULT 0,
ADD COLUMN cambio_entregado_calculado NUMERIC(19, 2) NOT NULL DEFAULT 0,
ADD COLUMN monto_neto_caja_calculado NUMERIC(19, 2) NOT NULL DEFAULT 0;
