ALTER TABLE dataico_configuracion ADD COLUMN IF NOT EXISTS token_cifrado TEXT;
-- No copia ni sobrescribe el token del YAML: seguirá usándose hasta guardar uno desde el POS.
