CREATE OR REPLACE FUNCTION pos_default_responsable_cierre()
RETURNS UUID
LANGUAGE SQL
STABLE
AS $$
    SELECT id
    FROM usuarios_sistema
    WHERE username = 'keli'
    LIMIT 1
$$;

ALTER TABLE cierres_diarios
ADD COLUMN IF NOT EXISTS responsable_usuario_id UUID;

UPDATE cierres_diarios
SET responsable_usuario_id = pos_default_responsable_cierre()
WHERE responsable_usuario_id IS NULL;

ALTER TABLE cierres_diarios
ALTER COLUMN responsable_usuario_id SET DEFAULT pos_default_responsable_cierre();

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'cierres_diarios'
          AND column_name = 'responsable_usuario_id'
          AND is_nullable = 'YES'
    ) THEN
        ALTER TABLE cierres_diarios
        ALTER COLUMN responsable_usuario_id SET NOT NULL;
    END IF;
END
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_cierres_diarios_responsable_usuario'
    ) THEN
        ALTER TABLE cierres_diarios
        ADD CONSTRAINT fk_cierres_diarios_responsable_usuario
        FOREIGN KEY (responsable_usuario_id) REFERENCES usuarios_sistema (id);
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_cierres_diarios_responsable_usuario_id
ON cierres_diarios (responsable_usuario_id);
