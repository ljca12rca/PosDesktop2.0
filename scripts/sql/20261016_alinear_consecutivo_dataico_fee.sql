-- La API confirmó que FEE1789500095 ya existe en Dataico.
-- Reserva el siguiente número sin retroceder si el consecutivo ya avanzó.
INSERT INTO dataico_consecutivos (prefijo, siguiente_numero)
VALUES ('FEE', 1789500096)
ON CONFLICT (prefijo) DO UPDATE
SET siguiente_numero = GREATEST(dataico_consecutivos.siguiente_numero, EXCLUDED.siguiente_numero);
