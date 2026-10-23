-- Configura o reinicia el próximo consecutivo de Dataico para FEG.
-- La siguiente emisión usará el número completo FEG91861.
CREATE TABLE IF NOT EXISTS dataico_consecutivos (
    prefijo VARCHAR(40) PRIMARY KEY,
    siguiente_numero BIGINT NOT NULL CHECK (siguiente_numero > 0)
);

INSERT INTO dataico_consecutivos (prefijo, siguiente_numero)
VALUES ('FEG', 91861)
ON CONFLICT (prefijo) DO UPDATE
SET siguiente_numero = EXCLUDED.siguiente_numero;
