# Facturación electrónica: persistencia e integración POS (puntos 7–11)

## Alcance

Se conserva Dataico como proveedor predeterminado. DIAN directa continúa limitada a
**borradores sin firma, sin envío y sin impresión fiscal**. Esta fase no implementa
firma XAdES, SOAP ni habilitación. No se ejecutaron operaciones fiscales reales.

Actualizacion posterior: el punto 12 agrega [firma XAdES y verificacion local](dian-firma-local.md)
con certificado de prueba, sin transmision y conservando el borrador original.

### 7. Evidencia persistente

- `documentos_fiscales`: snapshot versionado JSON, proveedor, ambiente, cuenta,
  cliente, número, total, SHA-256 y XML cuando se genera con DIAN directa.
- Los snapshots Dataico guardan su solicitud legada y los importes comerciales
  originales, **sin tokens ni secretos**. No se inventa un XML para Dataico:
  esa ruta conserva el documento JSON que realmente utiliza el proveedor.
  Antes de un reintento se comprueba que el adaptador reproduzca el JSON original;
  un cambio de mapper que altere ese cuerpo bloquea el envío en vez de modificar la evidencia.
- `eventos_fiscales`: historial de reserva, respuesta, incertidumbre o generación.
- Documentos y eventos son de solo inserción, también mediante triggers PostgreSQL.
  Cambios posteriores en cliente, venta o configuración no alteran la evidencia guardada.
- No se reconstruye un snapshot histórico con datos actuales: los documentos anteriores
  mantienen sus registros y se identifican como Dataico.

### 8. Proveedor de cada operación

`facturas_electronicas.proveedor` clasifica los documentos históricos como DATAICO.
`facturacion_operaciones` asocia una venta con un único proveedor y cliente fiscal.
El proveedor de una operación existente no puede cambiarse mediante una actualización SQL.
Cambiar la selección afecta a nuevas ventas; no vuelve a emitir documentos históricos.
Los borradores DIAN son previsualizaciones independientes y no una segunda emisión.

### 9. Idempotencia, reservas y reintentos

Antes del HTTP se confirma una transacción corta con el snapshot, operación y número
reservado. El número no se devuelve al contador si falla la comunicación.

| Estado | Comportamiento |
|---|---|
| EN_CURSO | Una solicitud es propietaria del intento. Otras no provocan otro POST. |
| CONFIRMADO | Se devuelve la respuesta guardada, incluso si cambió el proveedor activo. No significa aceptación DIAN. |
| INCIERTO | No se conoce el resultado remoto. Solo se consulta el número original; no se reemite automáticamente. |
| ERROR_SEGURO | Rechazo HTTP definitivo o fallo local comprobado antes del HTTP. Permite reintento explícito del mismo snapshot y número. |

Un proceso detenido deja EN_CURSO. Pasados tres minutos se permite una consulta de
recuperación, nunca un nuevo POST automático. La consulta debe devolver el mismo
número y un identificador de Dataico para confirmar la operación.

Los HTTP 400/401/403/404/422 recibidos en emisión se consideran rechazos definitivos.
Timeouts, otros errores HTTP y respuestas incompletas quedan inciertos. Si una consulta
no confirma el documento, permanece bloqueada una nueva emisión: es necesaria una
conciliación con el proveedor. No se ofrece un botón que borre esa protección.

Se conserva la corrección de consecutivo indicada explícitamente por Dataico: un solo
reintento, mismo prefijo y siguiente número que no haya sido reservado. Se guarda otro
snapshot, dejando el anterior intacto. Nunca se retrocede un contador ni se cambia
automáticamente de proveedor. La cuenta/conexión quedan comprobadas y bloqueadas
durante el uso del adaptador legado; cambiar una cuenta no redirige sus reintentos.

El registro legado de facturas tiene número completo único globalmente. Se verifica
antes de contactar Dataico; cambiar de cuenta/ambiente no permite reutilizar dentro del
POS un número completo ya registrado o reservado. Esa limitación histórica no se
elimina mediante una migración destructiva.

### 10. Configuración segura

- `facturacion_configuracion`: selección persistente DATAICO / DIAN_DIRECT y versión.
  La selección de la aplicación se administra desde el POS; la antigua propiedad
  experimental `pos.invoicing.provider` ya no gobierna el flujo comercial.
- `dian_configuraciones`: filas independientes PRUEBAS y PRODUCCION, sin copiar
  secretos entre ambientes. Incluyen emisor, domicilio DANE, software, SoftwareID,
  TestSetId, proveedor del software, resolución, vigencia/rango y número de previsualización.
- Clave técnica, PIN, certificado PKCS#12 y contraseña se cifran con AES-GCM reutilizando
  el mecanismo del proyecto. La clave fiscal está separada de la clave del token Dataico.
- Las respuestas solo indican si hay secretos configurados; nunca los devuelven.
  Vacío conserva el secreto anterior. Se rechaza una edición con versión obsoleta.
- Cargar un certificado comprueba formato, contraseña, clave privada y vigencia.
  **No firma XML ni acredita habilitación DIAN.**
- Consultar o editar configuración DIAN y cambiar proveedor requiere ADMIN en el backend.

La clave se conserva fuera del repositorio y de la base de datos:

- Windows: `%LOCALAPPDATA%/PosDesktop/secrets/fiscal.key`.
- Otros entornos: `~/.posdesktop/secrets/fiscal.key`.
- Puede configurarse con `pos.invoicing.key-file` / `POS_INVOICING_KEY_FILE`.
- Compose monta un volumen nuevo `fiscal_secrets` en `/var/posdesktop-fiscal-secrets`.
  No cambia la ruta ni las credenciales del token Dataico existente.

**Respaldar la base y la clave fiscal por separado.** Sin la clave no se pueden abrir
las credenciales cifradas; el programa falla de forma explícita y no crea una clave
sustituta para descifrar datos existentes. Una reinstalación debe conservar ese volumen
o archivo. No copiar claves a scripts SQL, capturas, logs ni ejemplos.

### 11. POS y navegación

Con la luz encendida:

1. ADMIN ve **Configuración electrónica** para selección de proveedor y datos DIAN.
   **Configuración Dataico** mantiene sus controles y credenciales anteriores.
2. **Documentos fiscales** permite consultar los últimos 200 registros, snapshots/XML,
   exportar evidencia, consultar intentos Dataico y reintentar rechazos seguros.
3. Con DIAN_DIRECT seleccionado, después de guardar la venta local se abre el formulario
   de borrador. Solicita cliente existente, ambiente, número de previsualización,
   responsabilidades/régimen, pago y clasificación explícita de cada artículo.
4. También puede retomarse una venta por su número local desde Documentos fiscales.
5. Los borradores se guardan con el estado **Borrador sin firma**, sin consumir
   numeración de producción ni marcar la venta como enviada o aceptada.

La vista reutiliza el tema, tarjetas, formularios, botones y tablas existentes. Tiene
carga, vacío, error y confirmación; formularios desplazables, campos asociados a sus
etiquetas y controles que se envuelven al reducir el ancho.

No se deducen obligaciones de impuesto cero. El XML debe coincidir con la venta guardada:
si se elige IVA pero esa venta no registra el impuesto, el generador informa la
inconsistencia en vez de cambiar sus importes o inventar una exención. La captura
comercial de impuestos de operaciones que el POS manual aún no representa debe
completarse antes de habilitar emisión directa de esas operaciones.

## Migración e instalación

Nueva migración Flyway: `V24__persistencia_fiscal_y_configuracion_dian.sql`.
Se incluye copia para ejecución manual en `scripts/sql/` y puede repetirse sin borrar
datos ni reiniciar consecutivos. Requiere el esquema previo hasta V23. Si la transacción
de una sesión SQL anterior quedó abortada, ejecutar ROLLBACK antes de empezar.

La API aplica la migración al arrancar mediante Flyway. No se modificó una migración
anterior ni se ejecutó V24 sobre bases del usuario. Para instalar esta fase se necesitan
**API actualizada y nuevo instalador POS**; no basta con cambiar únicamente el escritorio.
La clave fiscal del servidor debe conservarse entre actualizaciones.

## API

Todas las rutas están bajo `/api/v1` y requieren sesión POS.

| Operación | Ruta | Permiso |
|---|---|---|
| Proveedor activo | GET `/facturacion/proveedor` | VENTAS_VIEW |
| Cambiar proveedor | PUT `/facturacion/proveedor` | ADMIN |
| Configuración por ambiente | GET/PUT `/facturacion/dian/configuracion/{ambiente}` | ADMIN |
| Datos públicos para formulario | GET `/facturacion/dian/preparacion/{ambiente}` | VENTAS_VIEW |
| Venta por comprobante | GET `/facturacion/ventas/por-numero?numero=...` | VENTAS_VIEW |
| Generar/guardar borrador | POST `/facturacion/ventas/{ventaId}/borrador` | VENTAS_EDIT |
| Evidencia | GET `/facturacion/documentos` y `/{id}` | VENTAS_VIEW |
| Emisión Dataico | POST `/ventas/{ventaId}/factura-electronica` | VENTAS_EDIT |
| Reintento seguro | POST `/ventas/{ventaId}/factura-electronica/reintentar` | VENTAS_EDIT |
| Conciliar intento | POST `/ventas/{ventaId}/factura-electronica/consultar-intento` | VENTAS_EDIT |

## Verificación reproducible

`mvn test` ejecuta las pruebas unitarias y JavaFX. Las pruebas PostgreSQL están
deshabilitadas sin una URL explícita del entorno desechable y rechazan otras URLs.

```powershell
docker run --detach --rm --name codex-pos-fiscal-tests --publish 127.0.0.1:55439:5432 --env POSTGRES_USER=fiscal_test --env POSTGRES_PASSWORD=fiscal_test_only --env POSTGRES_DB=posdesktop_fiscal_test postgres:17
.\.tools\apache-maven-3.9.9\bin\mvn.cmd '-Dpos.fiscal.test.url=jdbc:postgresql://127.0.0.1:55439/posdesktop_fiscal_test' test
docker stop codex-pos-fiscal-tests
```

Ese contenedor es desechable y no monta volúmenes existentes. Las pruebas aplican
V1–V23, insertan historia y aplican V24; verifican preservación, bloqueo concurrente,
recuperación tras timeout, reintentos del snapshot original, inmutabilidad, aislamiento
de ambientes, cifrado y borradores sin consumo de secuencia. El proveedor remoto es
un mock: no se contacta DIAN ni Dataico.

Las pruebas JavaFX generan capturas en `pos-desktop/target/ui-review` para revisar
ancho normal y reducido, estados de carga/error y separación de ambientes.

Resultado verificado: **126 pruebas correctas, cero fallos, errores u omisiones**:
35 del núcleo, 63 de API (11 con PostgreSQL real) y 28 del escritorio. También se
verificaron el empaquetado de API/escritorio, la sintaxis del compose y dos ejecuciones
manuales consecutivas de V24 en la base desechable. No se generó un instalador ni se
actualizaron servicios o bases del cliente.
