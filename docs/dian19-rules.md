# DIAN directa: puntos 1–6 — 30 de octubre de 2026

Este incremento genera y verifica **borradores locales sin firma**, para venta nacional
estándar en COP (operación 10, documento 01). No habilita emisión directa ni reemplaza
Dataico. No reserva consecutivos ni guarda documentos o credenciales en base de datos.

## Fuentes y reproducción

- [Portal técnico DIAN](https://micrositios.dian.gov.co/sistema-de-facturacion-electronica/documentacion-tecnica/).
- [Anexo FE 1.9, 753 páginas](https://www.dian.gov.co/impuestos/factura-electronica/Documents/Anexo-Tecnico-Factura-Electronica-de-Venta-vr-1-9.pdf).
- [Caja oficial FE 1.9 v2026](https://www.dian.gov.co/impuestos/factura-electronica/Documents/Caja-de-herramientas-FE_V19_v2026.zip).
- [Resolución 227 de 2025, texto anotado](https://normograma.dian.gov.co/dian/compilacion/docs/resolucion_dian_0227_2025.htm),
  artículos 1.5.1.10.1–4: adopción/divulgación del anexo 1.9 y tablas referenciadas.
- [Orden administrativa DIAN 4/1989, anexo 3](https://normograma.dian.gov.co/dian/compilacion/docs/orden_administrativa_dian_0004_1989.htm):
  módulo 11 y vector 860324218 → DV 1. La validación matemática no acredita existencia en RUT.

Los originales descargados se inspeccionaron en `pos-invoicing/target/dian-reference`.
PDF SHA-256: `1b4022ac112232cd525a455432b2bdfc977d2edcf14c1c7aa26f8ba93fe47ded`.
ZIP SHA-256: `036eac8ca529ee7d13601eebcd77f89394bcd83fb1220f723616292d30685b39`.
`src/main/resources/dian19/provenance.json` fija URLs, fecha, archivos y hashes.

Para reproducir, colocar los dos originales como `anexo-fe-1.9.pdf` y
`herramientas-2026.zip` en esa carpeta y ejecutar `scripts/import-dian19-reference.py`
con Python/openpyxl. El importador no usa red, base de datos ni servicios fiscales.
Los recursos generados están versionables; las pruebas y el arranque no necesitan Python,
Excel ni descargar esquemas.

## Matriz del incremento

| Punto | Implementación | Referencia / verificación |
|---|---|---|
| 1 | Catálogos XLSX oficiales locales, 2670 entradas, manifiesto de procedencia | Ambiente, documento, operación, identificación, naturaleza, tributos, responsabilidades, régimen, pago, unidades, motivos, país y DANE; tarifas de IVA/INC/retenciones e IBUA/ICUI por año |
| 2 | Extensiones DIAN, resolución, vigencia/rango, proveedor/software, autoridad y QR; cabecera del perfil, naturaleza y domicilio | Anexo pp. 22–28; generación offline determinista, sin firma ficticia |
| 3 | CUFE SHA-384 y SoftwareSecurityCode SHA-384 | §11.2 pp.655–659: vector oficial p.657; §11.8: fórmula oficial, vector de seguridad sintético calculado independientemente |
| 4 | DV módulo 11, catálogos, municipio/departamento, moneda COP, pagos, límites decimales y HALF_EVEN | §5.2.1, 5.2.3; tablas 13; precios con hasta 6 decimales y bases nominales hasta 2 |
| 5 | Descuentos/cargos financieros globales, retenciones informativas, tributos nominales | FAU02–14, FAX07–12, §11.9.1; casos porcentuales, IBUA/100 ml e INCBolsas/INPP/ADV por unidad |
| 6 | Referencias XML oficiales y pruebas aritméticas, identificadores, XSD, seguridad y regresiones | Siete XML originales preservados; comparación semántica detallada exento/excluido; referencias de cabecera/extensiones de los siete |

La existencia de un código/tarifa en las tablas no determina que un producto deba
tributar así. El llamador debe proporcionar clasificación, naturaleza y forma de pago.
No se infieren obligaciones a partir de precios, NIT o tasa cero. IBUA e ICUI utilizan
solo años publicados en la caja descargada; un año desconocido falla antes de generar XML.

## Reglas de cálculo

- Por línea: bruto = cantidad × precio; neto = bruto − descuento + cargo. Redondeo
  monetario a dos decimales, al par (HALF_EVEN). IVA/INC se calculan sobre el neto.
- Exento tiene TaxTotal de tasa cero; excluido no tiene TaxTotal. TaxExclusiveAmount
  cuenta la base neta de cada línea tributada una sola vez, aunque tenga varios tributos.
- TaxTotal separado por tributo, con subtotales por tarifa. La base de los excluidos
  no se suma a TaxExclusiveAmount. TaxInclusiveAmount = netos + impuestos.
- Ajustes globales **financieros**: PayableAmount = TaxInclusiveAmount − descuentos
  globales + cargos globales + PayableRoundingAmount explícito (FAU14).
  No se redistribuyen silenciosamente sobre bases gravables. Si un descuento comercial
  modifica esas bases debe distribuirse explícitamente por línea antes de crear el snapshot.
- Retenciones 05/06/07: WithholdingTaxTotal separado, sin reducir PayableAmount
  (§11.9.1). El calculador expone su importe aparte; no inventa base ni aplicabilidad.
- Tributos nominales: cantidad física × tarifa / cantidad de referencia. IBUA usa
  referencia 100 y unidad MLT (mililitros del catálogo), los otros nominales soportados
  referencia 1. INCBolsas exige además la cantidad y unidad facturadas. La tarifa de bolsas,
  INPP o ADV debe suministrarse explícitamente, no se presume una tarifa legal.
- CUFE usa importes finales con dos decimales truncados al construir la cadena (§11.2),
  fecha ISO y hora -05:00. Incluye impuestos 01/04/03, total, identificaciones sin DV,
  clave técnica y ambiente. No usa UUID aleatorio ni identificador del programa como sustituto.
- SoftwareSecurityCode = SHA-384(SoftwareID + PIN + número completo), sin separadores.
  `DianSecrets` mantiene copias propias de char[], redacta toString y permite borrar las
  copias al cerrar. Clave técnica/PIN no se serializan ni registran. Esto no sustituye
  el futuro almacenamiento cifrado de configuración.

## Inconsistencias del material oficial

1. `DIAN_UBL_Structures.xsd`, `coID2Type/@schemeID`, enumera tipos de documento
   (11,12,13…31…), pero el anexo y los ejemplos emplean ahí el DV (0–9), y en
   `schemeName` el tipo 31. Incluso el propio ejemplo exento es rechazado por el XSD
   original. Se conserva **intacto** y se genera un segundo archivo explícitamente
   local `DIAN_UBL_Structures-profile19.xsd`, que cambia solamente esa enumeración
   a 0–9. El importador fija hashes y falla si cambia la estructura esperada.
   `OfflineUblValidator.dian19Published()` permite reproducir el rechazo original;
   `dian19()` valida con la variante documentada. No se afirma que la variante sea
   un archivo emitido por DIAN. Hay prueba tanto de la contradicción como de rechazo
   de DV fuera de 0–9. Debe contrastarse de nuevo antes de la habilitación.
2. Los XML oficiales incluyen CustomizationID antiguos (02/03/05), ProfileID abreviado,
   anticipos restados de totales y ejemplos saludables con cifras históricas. Se conservan
   como evidencia y referencia estructural, no como facturas vigentes aceptadas.
   `mixed.xml`/`withholding.xml` restan anticipo de PayableAmount, contrario a §11.9.2.
3. `ibua.xml` usa `ML`, mientras la tabla 13.3.6 publica `MLT`. Se genera MLT conforme
   al catálogo y FAX10. Ese ejemplo contiene tarifa nominal cero; no demuestra una tarifa
   vigente gravada. Para 2026 se usan las tarifas 0/40/68 de la tabla XLSX 13.3.11.
4. Los `.gc` del paquete no contienen todas las actualizaciones de las tablas XLSX.
   El catálogo de ejecución se obtiene de XLSX, con procedencia por archivo.

## Uso y validación

```java
try (var secrets = new DianSecrets(technicalKeyChars, softwarePinChars)) {
    var draft = new DianInvoiceDraftGenerator().generate(snapshot, profile, secrets);
    // XML local con CUFE y metadatos; readyToTransmit() siempre false.
    // Pendientes: FIRMA_XADES, HABILITACION_DIAN, TRANSPORTE_DIAN.
}
```

El bean de API está disponible para un futuro caso de uso local; no se agregó endpoint
de envío. La ruta comercial sigue en Dataico. El adaptador puro de ventas admite ajustes
globales y retenciones explícitos, compara descuentos/total/impuestos y no modifica entidades.

```powershell
.\.tools\apache-maven-3.9.9\bin\mvn.cmd test
```

Pruebas sin red fiscal ni base de datos: casos mixtos, redondeo, retenciones, descuentos,
impuestos nominales, DV, DANE, tarifas desconocidas, CUFE oficial, código del software,
secretos cerrados, determinismo, ambientes, esquemas originales/corregidos y XXE.
`target/examples/dian19-unsigned.xml` se regenera con datos ficticios durante las pruebas.

Resultado verificado: **109 pruebas, cero fallos, errores u omisiones** (35 del núcleo,
49 de API/Dataico y 25 de escritorio), Java 25. Mockito requiere poder adjuntar su
agente en esta máquina; la ejecución final se realizó fuera del sandbox de procesos.

Esto no implementa todas las reglas para todos los tipos de documento del anexo. No se
soportan aún exportación, AIU, mandatos, transporte, divisas, operación gratuita ni notas.
La verificación de todas las reglas DIAN y los vectores de habilitación seguirá siendo
necesaria al implementar firma/transporte. Los puntos 7–11 se incorporaron después y se
documentan en [Persistencia y POS](dian-persistencia-pos.md). El punto 12 incorpora
[firma XAdES local](dian-firma-local.md); el siguiente pendiente es empaquetado/transporte.
