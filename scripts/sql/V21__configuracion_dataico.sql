-- Ejecutar una sola vez; no reinicia los consecutivos actuales.
CREATE TABLE IF NOT EXISTS dataico_configuracion (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    prefijo VARCHAR(40),
    resolucion VARCHAR(40),
    ambiente VARCHAR(20),
    flexible BOOLEAN,
    version BIGINT NOT NULL DEFAULT 0
);
INSERT INTO dataico_configuracion (id, version) VALUES (1, 0) ON CONFLICT (id) DO NOTHING;
