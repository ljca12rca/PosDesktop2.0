-- Inicializa FEE si falta, conservando cualquier consecutivo configurado en la base.
-- La validación del rango de resolución corresponde a Dataico.
INSERT INTO dataico_consecutivos (prefijo, siguiente_numero)
SELECT 'FEE', GREATEST(1789500095::BIGINT, COALESCE(MAX(
    CASE WHEN numero_factura ~ '^FEE[0-9]+$' THEN
        substring(numero_factura FROM 4)::NUMERIC
    END
), 0)::BIGINT + 1)
FROM facturas_electronicas
WHERE numeracion = 'FEE'
ON CONFLICT (prefijo) DO NOTHING;
