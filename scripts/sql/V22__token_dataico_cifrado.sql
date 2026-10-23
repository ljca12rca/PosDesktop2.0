ALTER TABLE dataico_configuracion ADD COLUMN IF NOT EXISTS token_cifrado TEXT;
-- La clave de cifrado se genera localmente en el primer guardado, fuera de la base de datos.
