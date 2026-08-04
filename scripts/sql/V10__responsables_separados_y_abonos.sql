-- Migracion manual PostgreSQL: responsables de separados y abonos.
-- Es idempotente y debe ejecutarse despues de la estructura de usuarios y de V9.
-- Para despliegues de la API, Flyway ejecuta automaticamente la copia en
-- pos-api/src/main/resources/db/migration/V10__responsables_separados_y_abonos.sql.

ALTER TABLE separados
ADD COLUMN IF NOT EXISTS responsable_usuario_id UUID;

ALTER TABLE abonos_separado
ADD COLUMN IF NOT EXISTS responsable_usuario_id UUID;

UPDATE separados
SET responsable_usuario_id = pos_default_responsable_cierre()
WHERE responsable_usuario_id IS NULL;

UPDATE abonos_separado
SET responsable_usuario_id = pos_default_responsable_cierre()
WHERE responsable_usuario_id IS NULL;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM separados WHERE responsable_usuario_id IS NULL)
       OR EXISTS (SELECT 1 FROM abonos_separado WHERE responsable_usuario_id IS NULL) THEN
        RAISE EXCEPTION 'No fue posible asignar un responsable a los separados historicos. Verifica el usuario por defecto.';
    END IF;
END
$$;

ALTER TABLE separados
ALTER COLUMN responsable_usuario_id SET NOT NULL;

ALTER TABLE abonos_separado
ALTER COLUMN responsable_usuario_id SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_separados_responsable_usuario'
    ) THEN
        ALTER TABLE separados
        ADD CONSTRAINT fk_separados_responsable_usuario
        FOREIGN KEY (responsable_usuario_id)
        REFERENCES usuarios_sistema (id)
        ON DELETE RESTRICT;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_abonos_separado_responsable_usuario'
    ) THEN
        ALTER TABLE abonos_separado
        ADD CONSTRAINT fk_abonos_separado_responsable_usuario
        FOREIGN KEY (responsable_usuario_id)
        REFERENCES usuarios_sistema (id)
        ON DELETE RESTRICT;
    END IF;
END
$$;

CREATE INDEX IF NOT EXISTS idx_separados_responsable_usuario_id
ON separados (responsable_usuario_id);

CREATE INDEX IF NOT EXISTS idx_abonos_separado_responsable_usuario_id
ON abonos_separado (responsable_usuario_id);
