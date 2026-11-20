-- Solo para bases de DESARROLLO que ya aplicaron V24-V27 fiscales de esta rama.
-- No hace Flyway repair ni acepta checksums desconocidos. Respaldar antes de ejecutar.
-- Las instalaciones master con V24__sugerencias_articulos.sql no requieren este puente.
BEGIN;
SET LOCAL search_path TO public, pg_catalog;
LOCK TABLE public.flyway_schema_history IN EXCLUSIVE MODE;
DO $$
BEGIN
 IF NOT EXISTS(SELECT 1 FROM flyway_schema_history WHERE version='24' AND script='V24__persistencia_fiscal_y_configuracion_dian.sql') THEN
  IF EXISTS(SELECT 1 FROM flyway_schema_history WHERE version='24' AND script<>'V24__sugerencias_articulos.sql') THEN
   RAISE EXCEPTION 'V24 desconocida. No se modificara el historial.';
  END IF;
  RAISE NOTICE 'Sin historial fiscal antiguo V24. No se requiere conversion.';
  RETURN;
 END IF;
 IF EXISTS(
  SELECT 1 FROM flyway_schema_history h
  LEFT JOIN (VALUES
   ('24','V24__persistencia_fiscal_y_configuracion_dian.sql',1266155109),
   ('25','V25__devolucion_anulacion.sql',-2101056709),
   ('26','V26__transporte_dian_habilitacion.sql',664828647),
   ('27','V27__entrega_documentos_fiscales.sql',-795847173)
  ) m(version,script,checksum) ON h.version=m.version
  WHERE h.version IN ('24','25','26','27') AND
   (h.script IS DISTINCT FROM m.script OR h.checksum IS DISTINCT FROM m.checksum OR NOT h.success OR h.type<>'SQL')
 ) THEN RAISE EXCEPTION 'Historial fiscal distinto del verificado. Conversion cancelada sin cambios.'; END IF;
 IF EXISTS(SELECT 1 FROM flyway_schema_history WHERE version IN ('20261119.1','20261119.2','20261119.3','20261119.4')) THEN
  RAISE EXCEPTION 'Existen versiones fiscales nuevas y antiguas simultaneamente. Revise el historial.';
 END IF;
 -- V24 oficial de master, mismos efectos, con indices repetibles para no borrar existentes.
 ALTER TABLE articulos ALTER COLUMN nombre TYPE VARCHAR(200);
 CREATE INDEX IF NOT EXISTS idx_articulos_precio_activos ON articulos(precio_base) WHERE activo=TRUE;
 CREATE INDEX IF NOT EXISTS idx_articulos_nombre_normalizado ON articulos(lower(regexp_replace(trim(nombre),'[[:space:]]+',' ','g')));
 UPDATE flyway_schema_history h SET version=m.new_version,script=m.new_script
 FROM (VALUES
  ('24','20261119.1','V20261119_1__persistencia_fiscal_y_configuracion_dian.sql'),
  ('25','20261119.2','V20261119_2__devolucion_anulacion.sql'),
  ('26','20261119.3','V20261119_3__transporte_dian_habilitacion.sql'),
  ('27','20261119.4','V20261119_4__entrega_documentos_fiscales.sql')
 ) m(old_version,new_version,new_script) WHERE h.version=m.old_version;
 INSERT INTO flyway_schema_history(installed_rank,version,description,type,script,checksum,installed_by,execution_time,success)
 SELECT COALESCE(MAX(installed_rank),0)+1,'24','sugerencias articulos','SQL','V24__sugerencias_articulos.sql',1235724720,current_user,0,TRUE FROM flyway_schema_history;
 RAISE NOTICE 'Historial fiscal convertido; evidencia, configuracion y checksums conservados.';
END $$;
COMMIT;
