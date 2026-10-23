CREATE TABLE clientes_electronicos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo_identificacion VARCHAR(20) NOT NULL,
    tipo_persona VARCHAR(10) NOT NULL,
    identificacion VARCHAR(40) NOT NULL,
    digito_verificacion VARCHAR(1),
    razon_social VARCHAR(160) NOT NULL,
    primer_nombre VARCHAR(80),
    segundo_nombre VARCHAR(80),
    primer_apellido VARCHAR(80),
    segundo_apellido VARCHAR(80),
    email VARCHAR(120),
    telefono VARCHAR(40),
    direccion VARCHAR(250),
    ciudad_codigo_dane VARCHAR(10),
    departamento_codigo_dane VARCHAR(10),
    pais_codigo VARCHAR(2) NOT NULL DEFAULT 'CO',
    creado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_clientes_electronicos_tipo_persona CHECK (tipo_persona IN ('JURIDICA', 'NATURAL')),
    CONSTRAINT uk_clientes_electronicos_tipo_identificacion UNIQUE (tipo_identificacion, identificacion)
);

CREATE INDEX idx_clientes_electronicos_razon_social ON clientes_electronicos (razon_social);
