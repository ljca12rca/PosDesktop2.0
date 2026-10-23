ALTER TABLE facturas_electronicas ADD COLUMN IF NOT EXISTS cliente_email VARCHAR(120);

-- Completa el destinatario en facturas existentes sólo cuando el documento identifica un correo único.
UPDATE facturas_electronicas AS factura
SET cliente_email = cliente.email
FROM (
    SELECT identificacion, MIN(TRIM(email)) AS email
    FROM clientes_electronicos
    WHERE email IS NOT NULL AND TRIM(email) <> ''
    GROUP BY identificacion
    HAVING COUNT(DISTINCT LOWER(TRIM(email))) = 1
) AS cliente
WHERE factura.cliente_email IS NULL AND factura.cliente_identificacion = cliente.identificacion;
