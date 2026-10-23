-- Correccion historica de cierres diarios.
--
-- Recalcula todos los cierres CERRADOS a partir de las ventas de su fecha,
-- incluyendo TRANSFERENCIA_QR en total_final. El efectivo neto sigue siendo
-- exclusivo de pagos en EFECTIVO, por lo que el cambio solo se descuenta de
-- ese medio de pago.
--
-- Requisitos:
--   * PostgreSQL.
--   * La migracion V11__medio_pago_ventas.sql ya debe estar aplicada.
--   * Ejecutar con la API detenida para impedir ventas concurrentes.
--
-- Ejemplo:
--   psql -h localhost -p 55432 -U pos -d posdesktop \
--     -f scripts/sql/20260813_recalcular_cierres_incluyendo_transferencias.sql
--
-- El script es idempotente: puede ejecutarse nuevamente sin duplicar valores.

BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'ventas'
          AND column_name = 'medio_pago'
    ) THEN
        RAISE EXCEPTION
            'No existe ventas.medio_pago. Aplique primero la migracion V11__medio_pago_ventas.sql.';
    END IF;
END
$$;

-- Evita que una venta o un cierre concurrente deje el recalculo a medio camino.
LOCK TABLE cierres_diarios, ventas IN SHARE ROW EXCLUSIVE MODE;

-- Alinea las ventas historicas al cierre de su propio dia. La aplicacion usa
-- esta misma relacion para consultar los cierres en el futuro.
UPDATE ventas AS v
SET cierre_diario_id = c.id,
    estado = CASE
        WHEN v.estado = 'ANULADA' THEN 'ANULADA'
        ELSE 'CERRADA'
    END,
    actualizado_en = CURRENT_TIMESTAMP
FROM cierres_diarios AS c
WHERE c.estado = 'CERRADO'
  AND v.fecha_venta >= c.fecha_operacion::timestamp
  AND v.fecha_venta < (c.fecha_operacion + INTERVAL '1 day')
  AND (
      v.cierre_diario_id IS DISTINCT FROM c.id
      OR (v.estado IS DISTINCT FROM 'ANULADA' AND v.estado IS DISTINCT FROM 'CERRADA')
  );

WITH ventas_agregadas AS (
    SELECT
        c.id AS cierre_id,
        COUNT(v.id) FILTER (WHERE v.estado IS DISTINCT FROM 'ANULADA') AS cantidad_ventas,
        COALESCE(SUM(COALESCE(v.subtotal, 0)) FILTER (WHERE v.estado IS DISTINCT FROM 'ANULADA'), 0) AS subtotal,
        COALESCE(SUM(COALESCE(v.descuento_total, 0)) FILTER (WHERE v.estado IS DISTINCT FROM 'ANULADA'), 0) AS descuento,
        COALESCE(SUM(COALESCE(v.impuesto_total, 0)) FILTER (WHERE v.estado IS DISTINCT FROM 'ANULADA'), 0) AS impuesto,
        COALESCE(SUM(COALESCE(v.total, 0)) FILTER (WHERE v.estado IS DISTINCT FROM 'ANULADA'), 0) AS total_ventas,
        COALESCE(SUM(
            CASE
                WHEN v.estado IS DISTINCT FROM 'ANULADA'
                 AND COALESCE(v.medio_pago, 'EFECTIVO') <> 'TRANSFERENCIA_QR'
                THEN CASE
                    WHEN COALESCE(v.monto_recibido, 0) > 0 THEN v.monto_recibido
                    ELSE COALESCE(v.total, 0)
                END
                ELSE 0
            END
        ), 0) AS efectivo_recibido,
        COALESCE(SUM(
            CASE
                WHEN v.estado IS DISTINCT FROM 'ANULADA'
                 AND COALESCE(v.medio_pago, 'EFECTIVO') = 'TRANSFERENCIA_QR'
                THEN CASE
                    WHEN COALESCE(v.monto_recibido, 0) > 0 THEN v.monto_recibido
                    ELSE COALESCE(v.total, 0)
                END
                ELSE 0
            END
        ), 0) AS transferencias_recibidas,
        COALESCE(SUM(
            CASE
                WHEN v.estado IS DISTINCT FROM 'ANULADA'
                 AND COALESCE(v.medio_pago, 'EFECTIVO') <> 'TRANSFERENCIA_QR'
                THEN GREATEST(COALESCE(v.cambio_entregado, 0), 0)
                ELSE 0
            END
        ), 0) AS cambio_efectivo
    FROM cierres_diarios AS c
    LEFT JOIN ventas AS v ON v.cierre_diario_id = c.id
    WHERE c.estado = 'CERRADO'
    GROUP BY c.id
)
UPDATE cierres_diarios AS c
SET cantidad_ventas = a.cantidad_ventas,
    subtotal_calculado = ROUND(a.subtotal, 2),
    descuento_calculado = ROUND(a.descuento, 2),
    impuesto_calculado = ROUND(a.impuesto, 2),
    total_calculado = ROUND(a.total_ventas, 2),
    monto_recibido_calculado = ROUND(a.efectivo_recibido + a.transferencias_recibidas, 2),
    cambio_entregado_calculado = ROUND(a.cambio_efectivo, 2),
    monto_neto_caja_calculado = ROUND(
        a.efectivo_recibido
        - a.cambio_efectivo
        - COALESCE(c.base_caja, 0)
        - COALESCE(c.trabajadoras, 0)
        - COALESCE(c.ahorro, 0),
        2
    ),
    egresos = ROUND(COALESCE(c.trabajadoras, 0) + COALESCE(c.ahorro, 0), 2),
    total_final = ROUND(
        a.efectivo_recibido
        - a.cambio_efectivo
        - COALESCE(c.base_caja, 0)
        - COALESCE(c.trabajadoras, 0)
        - COALESCE(c.ahorro, 0)
        + a.transferencias_recibidas,
        2
    ),
    actualizado_en = CURRENT_TIMESTAMP
FROM ventas_agregadas AS a
WHERE c.id = a.cierre_id;

COMMIT;

-- Verificacion posterior: total_final debe incluir las transferencias.
SELECT
    fecha_operacion,
    cantidad_ventas,
    monto_recibido_calculado AS recibido_total,
    cambio_entregado_calculado AS cambio_efectivo,
    monto_neto_caja_calculado AS efectivo_neto,
    total_final AS total_cierre
FROM cierres_diarios
WHERE estado = 'CERRADO'
ORDER BY fecha_operacion DESC;
