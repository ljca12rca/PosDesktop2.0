# DIAN directa — base XML, actualizada 2026-10-30

**Estado: implementados los incrementos de base y puntos 1–11 del listado de pendientes.**
No instalar como sustituto de Dataico ni transmitir los borradores generados.
Detalle normativo, pruebas y limitaciones del incremento: [Reglas DIAN 1.9](dian19-rules.md).
Persistencia, configuración segura, reintentos y POS: [Puntos 7–11](dian-persistencia-pos.md).

## Análisis previo del repositorio

1. Maven multi-módulo: `pos-domain` (entidades JPA/documentos Mongo), `pos-api`
   (Spring Boot 4.1.0), `pos-desktop` (JavaFX 25). Compilación Java 25.
2. `VentasService` registra `Venta`/`DetalleVenta` localmente. La UI solicita después
   la emisión electrónica; un fallo externo no elimina la venta local.
3. Ya existe `ProveedorFacturacionElectronica` y `FacturacionElectronicaService`.
   El contrato mezcla emisión, consultas de terceros, estado y reenvío; no se reemplaza.
4. `DataicoFacturacionElectronicaProvider` concentra HTTP; `DataicoInvoiceMapper`
   construye el JSON. El mapper ya está separado: se reutiliza mediante adaptación.
5. `FacturacionVentaService` tiene acoplamiento a configuración, secuencia, excepción
   y códigos Dataico. Genera líneas con porcentaje IVA cero; el mapper actual no
   serializa impuestos de esas líneas. No se altera ese comportamiento en este incremento.
6. `ClienteElectronico`, `Articulo`, `Venta` y `DetalleVenta` contienen información
   comercial, pero no toda la configuración fiscal del emisor ni clasificación de
   exento/excluido. No se infieren esas obligaciones a partir de un precio o un cero.
7. `DataicoConfiguracionService`/`DataicoConsecutivoRepositorio` gestionan resolución,
   prefijo y próximo número; el token se cifra con `DataicoTokenCipher`. No se usan
   ni se modifican para previsualizar XML.
8. `FacturaElectronica` almacena referencia a venta, estados, mensajes, identificador
   Dataico y URL PDF; no contiene un snapshot fiscal completo ni proveedor histórico.
9. Consulta/acciones de facturas y configuración visual siguen orientadas a Dataico.
  En la base inicial no se añadieron columnas; V24 incorpora trazabilidad sin reemitir historia.
10. Pruebas existentes: configuración/cifrado/controlador Dataico, conexión dinámica,
    estados de correo, emisión/acciones de ventas, cálculo de cierres; cliente HTTP,
    descarga/impresión PDF y vistas JavaFX. JUnit/Mockito ya están disponibles.

## Decisión incremental

Añadir `pos-invoicing`, biblioteca sin Spring, JPA ni JavaFX. Contendrá snapshots,
aritmética decimal, puertos de capacidades, selección explícita y borradores UBL.
`pos-api` contendrá la capa anticorrupción de ventas y el adaptador al servicio
Dataico existente. No se cambia su JSON, autenticación, endpoints ni ruta comercial.

El borrador UBL es una herramienta de desarrollo local, **no una factura DIAN
expedida ni lista para transmitir**. La generación y la emisión son capacidades
separadas. Seleccionar DIAN_DIRECT para emisión debe fallar sin fallback a Dataico.

## Fuentes consultadas y alcance normativo

- [DIAN: documentación técnica](https://micrositios.dian.gov.co/sistema-de-facturacion-electronica/documentacion-tecnica/):
  anuncia Anexo FE 1.9 y caja de herramientas 1.9 actualizada para 2026.
- [Resolución 165 de 2023, texto anotado](https://normograma.dian.gov.co/dian/compilacion/docs/resolucion_dian_0165_2023.htm):
  contiene anotaciones de compilación en Resolución 227 de 2025 y modificaciones.
  No se debe usar el texto de 2023 ignorando dichas anotaciones.
- [OASIS UBL 2.1, esquemas oficiales](https://docs.oasis-open.org/ubl/os-UBL-2.1/xsd/maindoc/UBL-Invoice-2.1.xsd).

Se incorporaron catálogos XLSX de la caja 2026, extensiones DIAN, CUFE (vector oficial),
seguridad del software (fórmula oficial y vector sintético independiente), DV y reglas
del perfil nacional estándar COP. Se conserva el XSD DIAN original y una variante
local documentada para corregir su contradicción sobre schemeID/DV.
XSD UBL no implica aceptación DIAN. Siguen pendientes configuración fiscal cifrada y
persistente, snapshot persistente, trazabilidad/antiduplicado, firma, SOAP y habilitación.

## Implementación disponible

- `pos-invoicing/domain`: `FiscalInvoice` y sus records inmutables de terceros,
  residencia, numeración, pagos, líneas e impuestos. `Amounts` centraliza escala 2
  y HALF_EVEN para importes; cantidades y precios unitarios conservan precisión decimal.
- `InvoiceCalculator`: calcula descuentos/cargos por línea y globales financieros,
  tributos porcentuales o nominales y retenciones informativas. Rechaza totales declarados inconsistentes, vencimientos anteriores,
  fechas fuera de vigencia, consecutivos fuera del rango suministrado y offset distinto
  a -05:00. Excluido se representa sin tributo y exento como tributo de tasa cero.
- `application`: puertos independientes de metadatos/capacidades y generación.
  `ProviderRegistry` rechaza duplicados, proveedor ausente o capacidad no habilitada;
  no hace fallback ni emite por sí mismo. `InvoicingSettings` es configuración inmutable
  por instalación, todavía sin persistencia multiempresa ni secretos.
- `UblDraftGenerator`: StAX, UTF-8 y namespaces oficiales UBL 2.1. Facturas de una o
  varias líneas, terceros/direcciones, pagos, descuentos/cargos por línea, tributos y
  totales. El perfil y la nota declaran explícitamente que es un borrador, no un perfil
  DIAN validado. No incluye CUFE/UUID, extensiones DIAN ni una firma de relleno.
- `DianInvoiceDraftGenerator`: amplía el XML genérico con el perfil DIAN 1.9,
  extensiones, CUFE, seguridad del software, naturaleza, residencia DANE y forma de pago.
  Valida localmente; devuelve pendientes de firma/habilitación/transporte y nunca permite transmitir.
- `OfflineUblValidator`: XSD oficiales OASIS completos para Invoice y sus 13
  dependencias, incluidos en `src/main/resources/ubl21` con avisos originales.
  Bloquea DTD/entidades en facturas y resolución externa de esquemas. Solo resuelve
  imports internos bajo el classpath autorizado; no descarga nada al validar.
- `pos-api/.../integration/SaleFiscalSnapshotMapper`: adaptación de una venta
  persistida más contexto fiscal explícito. No modifica la venta ni accede a
  repositorios. Compara totales/impuestos tanto por línea como por documento.
  Rechaza clasificación faltante y descuentos globales sin contexto explícito verificable.
- `DataicoLegacyAdapter`: composición sobre `FacturacionElectronicaService`; delega
  el documento anterior sin reconstruirlo. No intenta traducir un snapshot nuevo a un
  JSON antiguo que hoy no representa correctamente todos sus impuestos/descuentos.
- `IndependentInvoicingConfig`: registra generadores y catálogo de proveedores.
  La selección comercial se persiste con V24. Las operaciones existentes conservan
  su proveedor; DIAN_DIRECT permanece sin transporte. Consultas históricas usan Dataico.
- Maven, Dockerfile y filtro de publicación consideran el módulo nuevo. El incremento
  7–11 añade migración, navegación y un volumen de claves fiscales al compose, sin desplegar
  imágenes ni aplicar cambios a bases del usuario.

## Cómo generar y validar localmente

Desde la raíz:

```powershell
.\.tools\apache-maven-3.9.9\bin\mvn.cmd -pl pos-invoicing -am test
```

El test `UblDraftTest.ejemploLocalGeneradoYValidado` crea
`pos-invoicing/target/examples/invoice-draft.xml`, con datos ficticios y validación
XSD local exitosa. El ejemplo es determinista, no requiere credenciales y no consume
consecutivos ni modifica el estado de una venta. Se regenera con las pruebas.

Uso desde Java (con un `FiscalInvoice` ya construido y calculado explícitamente):

```java
var draft = new UblDraftGenerator().generate(invoice);
var result = new OfflineUblValidator().validate(draft.xml());
if (!result.structurallyValid()) {
    throw new IllegalArgumentException(result.issues().toString());
}
// draft.state() == LOCAL_DRAFT_NOT_ISSUED; guardar solo como previsualizacion.
```

Los errores aritméticos son `InvoiceValidationException` con campo y regla. Los
errores estructurales llevan línea/columna y descripción. Ninguno equivale a rechazo
o aceptación oficial DIAN; no se guarda ningún estado fiscal a partir de ellos.

Los XSD ya están incluidos. Para descargarlos de nuevo en desarrollo, exclusivamente
desde el sitio oficial, se dispone de `scripts/download-ubl21-schemas.ps1`. No es un
paso de arranque, instalación ni ejecución de las pruebas.

## Selección de proveedor y seguridad

La migración V24 conserva DATAICO como selección inicial. ADMIN puede cambiarla desde
Configuración electrónica, visible con la luz encendida. La selección se guarda en
`facturacion_configuracion`; ya no se controla con la propiedad experimental del YAML.

DIAN_DIRECT solo permite borradores: no activa SOAP ni hace fallback a Dataico. Una
operación Dataico histórica conserva su proveedor y sus datos aunque cambie la selección.
La evidencia, reservas y estados de reintento se describen en la documentación 7–11.
No se hicieron llamadas fiscales reales ni cambios en bases del usuario.

## Verificación y pendientes precisos

Las pruebas se ejecutan con Maven/Java 25 e incluyen regresiones de API/Dataico y desktop.
El resultado de base 1–6 fue 109 pruebas correctas. La verificación actualizada se
documenta en [Persistencia y POS](dian-persistencia-pos.md).
Pruebas locales: IVA 19/5, múltiples productos, exento/excluido, redondeo, descuento/cargo
por línea, consistencia, datos faltantes/mal formados, snapshot, selección de proveedor,
delegación Dataico sin alterar documento, bloqueo de emisión directa, XML determinista,
caracteres especiales, errores XSD y bloqueo XXE. No se usan servicios DIAN/Dataico reales.

La firma XML XAdES y verificacion local se incorporaron en el punto 12:
ver [Firma local](dian-firma-local.md). No implementados todavia: empaquetado/transporte/respuestas DIAN y
habilitación. Tampoco operaciones especiales
(exportación, AIU, mandatos, transporte, moneda extranjera o documentos gratuitos).
Los contratos de esas operaciones deben añadirse cuando exista una implementación real
que los consuma; no se crean clases vacías ni simulaciones de aceptación.

Siguiente incremento (punto 13): empaquetado y transporte. Hasta verificar confianza del
certificado real y completar transporte y habilitación, el borrador no es expedible.
