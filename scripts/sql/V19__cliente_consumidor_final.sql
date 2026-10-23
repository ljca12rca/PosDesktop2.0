-- Cliente genérico para las ventas de mostrador del punto de venta configurado en el POS.
-- La dirección corresponde al lugar de entrega (sede del negocio), no a una residencia personal.
INSERT INTO clientes_electronicos (
    id, tipo_identificacion, tipo_persona, identificacion, razon_social,
    primer_nombre, primer_apellido, direccion, ciudad_codigo_dane,
    departamento_codigo_dane, pais_codigo, creado_en, actualizado_en
)
VALUES (
    gen_random_uuid(), 'CC', 'NATURAL', '222222222222', 'CONSUMIDOR FINAL',
    'CONSUMIDOR', 'FINAL', 'CALLE 28 # 29-18', '237', '05', 'CO', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
)
ON CONFLICT (tipo_identificacion, identificacion) DO NOTHING;
