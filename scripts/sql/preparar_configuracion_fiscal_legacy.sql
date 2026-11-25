-- Previo al consolidado fiscal, para tablas antiguas creadas por Hibernate.
-- Solo completa defaults de metadatos; conserva las configuraciones existentes.
DO $$
DECLARE tabla TEXT;
BEGIN
 FOREACH tabla IN ARRAY ARRAY['facturacion_configuracion','dian_configuraciones','dian_transporte_configuracion','fiscal_entrega_configuracion']
 LOOP
  IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name=tabla AND column_name='version') THEN
   EXECUTE format('ALTER TABLE public.%I ALTER COLUMN version SET DEFAULT 0',tabla);
  END IF;
 END LOOP;
 IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema='public' AND table_name='facturacion_configuracion' AND column_name='proveedor') THEN
  ALTER TABLE public.facturacion_configuracion ALTER COLUMN proveedor SET DEFAULT 'DATAICO';
 END IF;
END $$;
