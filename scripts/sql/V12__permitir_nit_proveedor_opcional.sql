-- Migracion manual PostgreSQL: permite registrar proveedores sin NIT.
-- La restriccion UNIQUE actual se conserva: solo los NIT informados deben ser unicos.
-- La API la ejecuta automaticamente mediante Flyway V12 al desplegar la version nueva.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'proveedores'
          AND column_name = 'nit'
          AND is_nullable = 'NO'
    ) THEN
        ALTER TABLE proveedores
        ALTER COLUMN nit DROP NOT NULL;
    END IF;
END
$$;
