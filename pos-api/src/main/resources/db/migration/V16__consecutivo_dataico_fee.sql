INSERT INTO dataico_consecutivos (prefijo, siguiente_numero)
VALUES ('FEE', 999999999)
ON CONFLICT (prefijo) DO UPDATE
SET siguiente_numero = EXCLUDED.siguiente_numero;
