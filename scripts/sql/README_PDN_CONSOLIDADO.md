# Consolidado seguro de pendientes para producción — 2026-10-23

Archivo a ejecutar: **`20261023_pdn_consolidado_pendientes_git_seguro.sql`**.

Actualizado el 2026-10-28: tambien crea `willinton` y `usuario`, ambos con clave inicial
`usuario123*` y rol **CAJERO** (no administrador). Las claves se guardan como hash BCrypt.
Si ya existen, no cambia su clave, estado ni roles. Cambiar las claves iniciales antes de entregar
el equipo; no ejecutar nuevamente para intentar restablecer una clave. El SQL contiene la clave
inicial de instalacion: no usarla como clave definitiva ni publicar este archivo con credenciales reales.

Está preparado a partir de los SQL que estaban pendientes en Git al iniciar esta tarea:
V12 a V23, sus copias manuales de `scripts/sql`, y los dos correctivos fechados.
No concatena los archivos de desarrollo: varios contienen números de prueba y actualizaciones históricas.
Los originales se conservaron sin editar.

## Alcance y condiciones

- PostgreSQL 13 o superior, esquema `public`.
- La base de producción ya debe tener la instalación V1 a V11. **No es un instalador de una base vacía**.
- API y cualquier otro escritor detenidos durante la ejecución.
- Respaldo previo verificado, espacio suficiente y usuario propietario de las tablas.
- Ejecutar todo el archivo en una sola conexión, sin seleccionar fragmentos.
- Todo está en una transacción. Un error, un bloqueo de más de 5 segundos o una sentencia de más de
  120 segundos aborta la ejecución; no continuar ignorando errores.

## Qué conserva

No hay sentencias `UPDATE`, `DELETE` ni `TRUNCATE` sobre datos de negocio.
Conserva ventas, detalles, cierres, separados, abonos, proveedores, clientes existentes, correos históricos,
tokens, Account ID, resolución, ambiente, versiones de configuración y consecutivos guardados.
V12 sí relaja `proveedores.nit` para permitir NULL; no quita la unicidad ni cambia ningún NIT almacenado.

Crea tablas, columnas, índices y restricciones faltantes. No convierte tipos ni recorta textos.
Si una tabla con datos carece de una columna obligatoria sin un valor neutro seguro, aborta y pide revisión.
Si hay duplicados, huérfanos, NULL incompatibles o tipos/longitudes incompatibles con las restricciones nuevas,
no los corrige ni los elimina: la transacción falla para que se revisen con autorización aparte.

Inserta los siguientes registros funcionales si no existen:

1. Configuración singleton `id=1`, versión 0, **Dataico deshabilitado** y sin credenciales ni numeración de pruebas.
   Si la fila ya existe, se conserva íntegra, incluso su estado de habilitación.
2. Consumidor final `CC / 222222222222`, sin dirección inventada. Si ya existe se conserva íntegro.
   Completar su dirección/departamento/ciudad reales desde el POS antes de facturar para él.
3. Usuarios `willinton` y `usuario` con rol CAJERO, segun la actualizacion indicada arriba.
   Crea el rol CAJERO y sus cinco permisos basicos de V8 si faltan. No modifica roles ni permisos
   existentes; si estan desactivados, aborta para revision. Requiere las tablas de seguridad de V8
   y permiso para instalar `pgcrypto` si aun falta.

La tabla de consecutivos se crea si falta, pero **no se inserta ni se modifica ningún consecutivo**.
Configurar el próximo número real y revisar todos los datos de producción en el POS antes de habilitar Dataico.
El script no modifica el YAML: sin revisión del operador, sus valores iniciales aún podrían ser los del equipo de pruebas.

## Cobertura de todos los pendientes detectados

| Origen | Tratamiento en el consolidado |
| --- | --- |
| `pos-api/src/main/resources/db/migration/V12__permitir_nit_proveedor_opcional.sql` y `scripts/sql/V12__permitir_nit_proveedor_opcional.sql` | Relaja NOT NULL de NIT; conserva su valor y unicidad. |
| `pos-api/src/main/resources/db/migration/V13__clientes_facturacion_electronica.sql` y `scripts/sql/V13__clientes_facturacion_electronica.sql` | Clientes, campos, clave documental, índice y validación de naturaleza, idempotentes. |
| `pos-api/src/main/resources/db/migration/V14__facturas_electronicas_dataico.sql` | Facturas, vínculo a venta, claves e índices. No existe una copia manual V14 en el inventario inicial. |
| `pos-api/src/main/resources/db/migration/V15__consecutivo_dataico.sql` y `scripts/sql/V15__consecutivo_dataico.sql` | Solo estructura; excluye el reinicio FEG91861. |
| `pos-api/src/main/resources/db/migration/V16__consecutivo_dataico_fee.sql` y `scripts/sql/V16__consecutivo_dataico_fee.sql` | Excluye la asignación FEE999999999. No tiene DDL adicional pendiente. |
| `pos-api/src/main/resources/db/migration/V17__consecutivo_dataico_pruebas.sql` y `scripts/sql/V17__consecutivo_dataico_pruebas.sql` | Excluye el UPDATE a 994500001. No tiene DDL adicional pendiente. |
| `pos-api/src/main/resources/db/migration/V18__corregir_consecutivo_dataico_fuera_de_rango.sql` y `scripts/sql/V18__corregir_consecutivo_dataico_fuera_de_rango.sql` | No inicializa FEE con números del equipo de desarrollo. |
| `pos-api/src/main/resources/db/migration/V19__cliente_consumidor_final.sql` y `scripts/sql/V19__cliente_consumidor_final.sql` | Inserta únicamente si falta, sin copiar la dirección fija de desarrollo. |
| `pos-api/src/main/resources/db/migration/V20__correo_destinatario_factura_electronica.sql` y `scripts/sql/V20__correo_destinatario_factura_electronica.sql` | Añade `cliente_email` si falta. **No ejecuta el backfill UPDATE de correos existentes**. |
| `pos-api/src/main/resources/db/migration/V21__configuracion_dataico.sql` y `scripts/sql/V21__configuracion_dataico.sql` | Configuración; la fila nueva inicia deshabilitada. Las filas existentes no se modifican. |
| `pos-api/src/main/resources/db/migration/V22__token_dataico_cifrado.sql` y `scripts/sql/V22__token_dataico_cifrado.sql` | Columna del token cifrado; no copia ni reemplaza tokens. |
| `pos-api/src/main/resources/db/migration/V23__conexion_dataico_configurable.sql` y `scripts/sql/V23__conexion_dataico_configurable.sql` | Account ID, habilitación, URL y tiempos de conexión. |
| `scripts/sql/20260813_recalcular_cierres_incluyendo_transferencias.sql` | **Excluye su UPDATE de ventas y cierres históricos**. Requiere aprobación y revisión independiente de cifras. |
| `scripts/sql/20261016_alinear_consecutivo_dataico_fee.sql` | **Excluye su inserción/avance del consecutivo de pruebas**. |

V1..V11, el full-init y los volcados/migradores legacy no estaban pendientes en el inventario inicial y no se vuelven a ejecutar.

## Muy importante: Flyway y arranque de la API

Este consolidado **no escribe ni falsifica `flyway_schema_history`**. No marca V12..V23 como ejecutadas,
porque su contenido seguro deliberadamente no es el mismo que los originales de pruebas.

Después de ejecutarlo, **no permitir que Flyway ejecute automáticamente esos originales sobre producción**:
además de errores por tablas ya existentes, V15..V17 podrían cambiar la numeración y V20 modificar correos históricos.

Para esta instalación manual, definir en el servicio `api` del compose de producción, antes de recrearlo:

```yaml
services:
  api:
    environment:
      SPRING_FLYWAY_ENABLED: "false"
```

Es una variable del **contenedor API**, no de PostgreSQL. Reiniciar solamente el contenedor no incorpora cambios
de su `environment`; hay que recrearlo con el compose correspondiente. Conservar sus otros valores y volúmenes.
No reactivar Flyway ni ejecutar `repair`, `baseline`, full-init o los originales por separado a ciegas.
Si producción utiliza Flyway de forma obligatoria, coordinar antes un despliegue con un historial y paquete de migraciones
de producción revisados: **no aplicar este archivo y luego arrancar el paquete original esperando que se concilien solos**.
Las futuras migraciones en una instalación manual también deben aplicarse mediante un procedimiento controlado.

## Ejecución en el PC de producción con Docker (PowerShell)

Cambiar nombres/usuario/base si allí son distintos. Estos comandos se ejecutan **en el PC de producción**,
no en el equipo de desarrollo. Confirmar el destino antes de cada paso.

### 1. Detener la API y respaldar

```powershell
docker stop posdesktop-api
docker exec posdesktop-postgres pg_dump -U pos -d posdesktop -Fc -f /tmp/posdesktop_antes_pendientes_20261023.dump
docker exec posdesktop-postgres pg_restore --list /tmp/posdesktop_antes_pendientes_20261023.dump
docker cp posdesktop-postgres:/tmp/posdesktop_antes_pendientes_20261023.dump .\posdesktop_antes_pendientes_20261023.dump
```

**Detenerse si cualquiera devuelve error.** Conservar y proteger el archivo `.dump` fuera de Git y fuera del contenedor.
No usar `docker exec ... pg_dump -Fc > respaldo.dump` en Windows PowerShell antiguo: su redirección puede alterar binarios.
La comprobación de listado valida el formato; antes del despliegue definitivo es recomendable restaurarlo en una base
separada de ensayo y comprobar sus datos. No restaurar encima de producción sin un plan explícito.

Si ya existe ese respaldo, darle otro nombre para no sobrescribir uno anterior.
Respaldar también, por separado, `dataico-token.key` del usuario/servidor que ejecuta la API si ya hay un token cifrado.
La base de datos por sí sola no contiene la clave de descifrado. No guardar esa clave en Git.

### 2. Copiar y ejecutar solamente el consolidado

Desde la raíz del repositorio (o ajustar la ruta al archivo que se llevó a producción):

```powershell
docker cp .\scripts\sql\20261023_pdn_consolidado_pendientes_git_seguro.sql posdesktop-postgres:/tmp/posdesktop_pendientes_pdn.sql
docker exec posdesktop-postgres psql -X -v ON_ERROR_STOP=1 -U pos -d posdesktop -f /tmp/posdesktop_pendientes_pdn.sql
```

El resultado debe finalizar en **COMMIT**, sin ERROR. En caso de error, no arrancar la API para que intente arreglarlo:
revisar el motivo y el esquema primero. Puede repetir este mismo consolidado cuando se haya resuelto el problema;
no repetir los archivos de pruebas para intentar desbloquearlo.

Con PostgreSQL nativo, alternativa equivalente:

```powershell
psql -h localhost -p 5432 -U pos -d posdesktop -v ON_ERROR_STOP=1 -f .\scripts\sql\20261023_pdn_consolidado_pendientes_git_seguro.sql
```

### 3. Revisar y arrancar

- Recrear la API actualizada con la configuración de Flyway indicada arriba, sin borrar volúmenes.
- No ejecutar `docker compose down -v`.
- Abrir el POS como ADMIN y encender la luz electrónica para entrar a Configuración Dataico.
- Revisar datos reales de producción: Account ID, token, resolución, ambiente, prefijo y próximo número.
- Revisar la dirección real del consumidor final si se acaba de crear.
- Solo después habilitar la conexión Dataico y hacer una prueba de emisión controlada.
- Comprobar que el cierre conserva sus cifras previas y que los nuevos documentos se guardan localmente.

## Verificación realizada en desarrollo

Se ejecutó `test-pdn-consolidado.ps1` en una base temporal generada, **nunca en la base real ni en producción**:

1. Instalación V1..V11 con proveedor, venta y cierre: compara huellas de todas las tablas base antes/después.
2. Instalación ya actualizada con clientes, factura, token ficticio, configuración y consecutivo propio:
   ejecuta dos veces y compara todas las filas. No cambia ni el correo histórico NULL ni la dirección existente.
3. Tablas pobladas con columnas opcionales faltantes: completa columnas y conserva los campos originales.
4. Falta una columna obligatoria en una tabla con datos: falla y comprueba rollback tanto de filas como del DDL previo.

La base temporal se elimina al terminar. Esto prueba los escenarios descritos, pero no sustituye un respaldo ni
conocer el esquema real de producción: ante discrepancias, el comportamiento previsto es abortar sin inventar datos.

Para repetir exclusivamente esas pruebas en un PostgreSQL Docker de desarrollo:

```powershell
.\scripts\sql\test-pdn-consolidado.ps1
```
