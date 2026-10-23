-- NULL mantiene los valores actuales del YAML hasta guardar desde el POS.
ALTER TABLE dataico_configuracion ADD COLUMN IF NOT EXISTS account_id VARCHAR(36);
ALTER TABLE dataico_configuracion ADD COLUMN IF NOT EXISTS habilitado BOOLEAN;
ALTER TABLE dataico_configuracion ADD COLUMN IF NOT EXISTS base_url VARCHAR(200);
ALTER TABLE dataico_configuracion ADD COLUMN IF NOT EXISTS connect_timeout_seconds INTEGER;
ALTER TABLE dataico_configuracion ADD COLUMN IF NOT EXISTS read_timeout_seconds INTEGER;
