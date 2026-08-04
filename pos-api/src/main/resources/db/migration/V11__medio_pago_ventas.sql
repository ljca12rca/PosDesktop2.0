-- Clasifica las ventas por medio de pago para separar efectivo fisico de transferencias QR.

ALTER TABLE ventas
ADD COLUMN IF NOT EXISTS medio_pago VARCHAR(30);

UPDATE ventas
SET medio_pago = 'EFECTIVO'
WHERE medio_pago IS NULL;

ALTER TABLE ventas
ALTER COLUMN medio_pago SET DEFAULT 'EFECTIVO';

ALTER TABLE ventas
ALTER COLUMN medio_pago SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'ck_ventas_medio_pago'
    ) THEN
        ALTER TABLE ventas
        ADD CONSTRAINT ck_ventas_medio_pago
        CHECK (medio_pago IN ('EFECTIVO', 'TRANSFERENCIA_QR'));
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_ventas_medio_pago
ON ventas (medio_pago);
