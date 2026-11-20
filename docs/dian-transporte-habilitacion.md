# Transporte y respuestas DIAN en PRUEBAS

Implementa empaquetado ZIP, SOAP 1.2 firmado, recepción del TrackId, consulta de estado
y conservación de respuestas para Invoice, CreditNote y DebitNote. Dataico conserva
su flujo y credenciales existentes.

## Alcance y modos

- **SIMULACION**, predeterminado: no abre conexiones. Construye y comprueba el mismo
  SOAP firmado, verifica la huella del ZIP y devuelve respuestas locales al parser.
  El primer seguimiento queda pendiente; el segundo termina `SIMULACION_VALIDADO`.
  No equivale a aceptación, habilitación ni documento expedido ante la DIAN.
- **HABILITACION**: adaptador HTTP implementado para el destino oficial fijo
  `https://vpfe-hab.dian.gov.co/WcfDianCustomerServices.svc`.
  La aplicación bloquea solicitudes reales mientras la copia firmada solo tenga
  validación criptográfica local. Falta verificar cadena de confianza, identidad
  fiscal y revocación del certificado real. Seleccionar este modo no elimina esa
  condición ni convierte el certificado autofirmado en uno válido para DIAN.
- Producción sigue deshabilitada. No se enviaron documentos reales durante el desarrollo.
  No necesitas comprar todavía el certificado para ejecutar las pruebas locales.

## Uso en el POS

1. En Configuración electrónica, PRUEBAS, registra emisor, software y TestSetId.
   Carga el `.p12` de pruebas con su contraseña. El modo de transporte lo cambia ADMIN.
2. En Documentos fiscales, abre el borrador y firma una copia. También aplica a
   Devolución y Anulación y a los ajustes de valor.
3. Abre la copia firmada y pulsa **Transporte DIAN**.
4. **F6** prepara un ZIP; **F9** envía/simula; **F10** consulta.
   Tab, flechas y Enter permiten recorrer y ejecutar los controles.
5. La vista conserva paquetes anteriores, permite guardar su ZIP y muestra
   seguimiento, códigos, errores, SOAP recibido y ApplicationResponse cuando exista.

El XML firmado se empaqueta sin modificar bytes. Un archivo por ZIP facilita la
correlación CUFE/CUDE. Los nombres usan NIT con diez posiciones, código de software
propio `000`, año y contador anual hexadecimal, separado de la numeración fiscal.
Preparar un archivo no incrementa consecutivos Dataico ni modifica ventas o caja.

## Estados y recuperación

`PREPARADO → EN_CURSO → RECIBIDO → PENDIENTE → ACEPTADO / RECHAZADO`.
Un error técnico, respuesta no correlacionada o timeout deja `INCIERTO`.
Todos los estados simulados llevan el prefijo `SIMULACION_` en el API y la pantalla;
el resultado positivo local se llama `SIMULACION_VALIDADO`, no `DIAN_ACEPTADO`.

- `SendTestSetAsync` devuelve ZipKey; solo acredita recepción del paquete.
- `GetStatusZip` consulta ese identificador. Sin ZipKey se usa `GetStatus` con CUFE/CUDE.
- Solo se acepta un resultado correlacionado con `IsValid=true`, código `00`, sin
  errores y con la clave del documento esperado. Código `99` negativo es rechazo;
  `66`, `90` y `98` son pendientes. Los resultados ambiguos no acreditan aceptación.
- No se repite el POST ante errores o reinicios. Se consulta el intento existente.
  Una reclamación queda protegida tres minutos; transcurrido ese plazo puede
  recuperarse mediante consulta, nunca reenviando el mismo paquete.
- Paquete, modo, TestSetId, huella y certificado quedan fijos. Cambiar configuración
  no redirige un seguimiento anterior. Se exige el certificado original para consultarlo.
- Una copia adicional con el mismo número queda bloqueada mientras exista un intento
  no rechazado. Si el paquete nunca se envió, **Descartar sin enviar** lo deja
  `CANCELADO` y permite firmar otra copia con la configuración corregida. Conserva
  la evidencia y el contador de archivo. Después del primer intento no se permite
  descartarlo. Tampoco descartar ajustes con un envío activo, incierto o aceptado.

## Persistencia y despliegue

V20261119.3 es aditiva y conserva ventas, clientes, consecutivos fiscales y credenciales actuales.
Requiere V20261119.1 y V20261119.2 de esta rama. Flyway la aplica al actualizar el API; para instalación
manual está `scripts/sql/V20261119_3__transporte_dian_habilitacion.sql`.
No ejecutar la versión manual además de Flyway sin coordinar su historial.

Tablas nuevas: `dian_transporte_configuracion`, `dian_archivos_consecutivos`,
`dian_envios`, `dian_intercambios`. Triggers protegen contenido, destino y evidencia
de respuestas contra cambios/borrados. Se conserva SOAP, estado HTTP y diagnóstico;
los logs operativos solo incluyen operación, identificador, modo y nombre de archivo.
No se guardan contraseñas ni claves privadas en los intercambios.

API bajo `/api/v1/facturacion/dian/transporte`:

- `GET/PUT /configuracion`: leer modo / cambiarlo como ADMIN, con versión.
- `POST/GET /documentos/{id}`: preparar paquete / listar paquetes de la copia firmada.
- `POST /{id}/enviar`, `POST /{id}/consultar`.
- `POST /{id}/descartar`: exclusivamente paquetes que nunca se enviaron.
- `GET /{id}`: resultado y respuestas; `GET /{id}/zip`: descargar evidencia.

Lectura requiere VENTAS_VIEW; preparación, envío y consulta requieren VENTAS_EDIT.
Actualizar tanto el API como el cliente de escritorio para usar esta pantalla.

## Verificación local

Pruebas de ZIP exacto y nombres para los tres XML; firma SOAP, alteración, vencimiento,
destino y acciones; parser de aceptación/rechazo/pendiente/Fault, claves ajenas,
duplicados y XXE; HTTP local sin redirecciones; PostgreSQL desechable, concurrencia,
idempotencia, timeout, inmutabilidad, protección de ajustes y bloqueo de red real
con certificado local; JavaFX con F6/F9/F10, estados vacío/error/resultado y anchos
960/640. Las pruebas usan certificados ficticios y servidores locales.

Resultado: **176 pruebas correctas**, cero fallos, errores u omisiones: 57 del núcleo,
87 del API (21 con PostgreSQL desechable) y 32 del escritorio. API y cliente
empaquetados con Maven. Firma SOAP y sus cinco referencias verificadas además con
`lxml` y `cryptography`, independientemente de Java XML DSig. Script SQL manual
comprobado con ejecución repetida sobre la base desechable.

Windows puede reservar el puerto 55439 para Hyper-V. La prueba PostgreSQL admite
también 55445, exclusivamente en localhost y con base `posdesktop_fiscal_test`.
No admite bases del usuario ni producción.

## Contratos oficiales consultados

- [Documentación técnica DIAN](https://micrositios.dian.gov.co/sistema-de-facturacion-electronica/documentacion-tecnica/).
- [Anexo técnico 1.9](https://www.dian.gov.co/impuestos/factura-electronica/Documents/Anexo-Tecnico-Factura-Electronica-de-Venta-vr-1-9.pdf), secciones 6.5.7–6.5.8 y 7.2.
- [WSDL oficial de habilitación](https://vpfe-hab.dian.gov.co/WcfDianCustomerServices.svc?wsdl), operaciones SendTestSetAsync, GetStatusZip y GetStatus; políticas WS-Addressing y X509 WS-Security.

La firma del sobre usa RSA-SHA256, referencias con SHA256 y canonicalización exclusiva.
Protege destino, cuerpo, Timestamp, Action y MessageID. ThumbprintSHA1 es el identificador
del certificado exigido por la política WSDL, no el algoritmo de firma del mensaje.
La respuesta se transporta por HTTPS. El incremento de [PDF y entrega local](dian-entrega-local.md)
incorpora verificacion independiente de firma y correlacion del ApplicationResponse,
con confianza explicita por huellas configuradas. La prueba oficial con certificado real
del emisor y su verificacion de confianza/revocacion siguen pendientes.
