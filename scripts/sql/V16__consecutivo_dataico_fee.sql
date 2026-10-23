-- Configura el próximo consecutivo de la numeración FEE reconocida por Dataico.
-- La siguiente emisión usará FEE999999999.
INSERT INTO dataico_consecutivos (prefijo, siguiente_numero)
VALUES ('FEE', 999999999)
ON CONFLICT (prefijo) DO UPDATE
SET siguiente_numero = EXCLUDED.siguiente_numero;
