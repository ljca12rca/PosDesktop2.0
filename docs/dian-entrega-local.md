# PDF, validacion de respuestas y entrega sin certificado real

Este incremento completa el recorrido local antes de comprar el certificado del emisor:
validacion criptografica del ApplicationResponse, PDF propio con QR, contenedor
AttachedDocument de prueba, SMTP configurable, historial de entrega y respaldo cifrado.
Dataico y la venta normal conservan sus flujos anteriores.

## Uso desde el POS

En **Documentos fiscales → Ver evidencia → PDF y correo de prueba**, elige A4,
80 mm o 58 mm. Puedes guardar el PDF, imprimir a tamaño real o guardar el ZIP.
Los mismos controles sirven para factura, Devolucion/Anulacion y ajuste de valor.

- **F7** guarda PDF; **F8** imprime; **F9** procesa el correo; **F10** actualiza historial.
- Tab y flechas recorren campos; Enter ejecuta el control enfocado.
- El correo del cliente aparece como sugerencia editable; cambiarlo aqui no edita al cliente.
- Abrir la vista, generar PDF o guardar el ZIP nunca envia un correo.
- **Nueva entrega** crea una nueva solicitud explicita. Si una anterior tiene resultado
  incierto para el mismo documento y destinatario, el servidor bloquea duplicados.

El PDF se lee del XML inmutable, incluyendo nombres, domicilio, identificacion,
fecha, numeracion/resolucion, autorizacion, lineas, impuestos, retenciones informativas,
total, CUFE/CUDE y enlace QR del ambiente. No usa precios o nombres editados posteriormente.
El QR incluye zona blanca y se verifica mediante decodificacion automatica.
Documentos largos tienen saltos de pagina; no se reducen para forzarlos en una sola hoja.
La impresion usa dimensiones del PDF y escala real. La aceptacion por la cola no confirma
salida fisica del papel. Si el controlador sustituye el ancho, se avisa para evitar cortes.

## Configuracion SMTP y confianza

En **Configuracion electronica → PDF, correo y confianza de respuestas**, ADMIN configura:
modo, servidor, puerto, STARTTLS obligatorio o SSL, usuario, remitente y nueva contraseña.
Vacio conserva la contraseña anterior; la API nunca la devuelve. Queda cifrada con una
clave independiente, `delivery.key`. Fuera de localhost se exige TLS y comprobacion de
identidad del servidor. El modo sin TLS solo permite un servidor de pruebas en localhost.

**SIMULACION** no abre conexiones y termina `SIMULADO`. **SMTP** envia realmente un
correo de prueba al destinatario indicado, solo por accion explicita. Asunto, cuerpo,
PDF y nombres de archivos identifican la prueba. `ENVIADO` significa que el servidor
SMTP acepto el mensaje, no que el cliente lo recibio o leyo. Un timeout deja `INCIERTO`;
no hay reenvio automatico. Un fallo de conexion/TLS o autenticacion anterior a SMTP DATA
queda `RECHAZADO`, permite corregir la configuracion y crear otra solicitud.

La solicitud conserva destinatario, modo, version de configuracion, ZIP exacto, hash
y resultado. Cambiar SMTP no modifica entregas anteriores. Se utiliza Message-ID estable
por solicitud; el bloqueo transaccional y la clave idempotente evitan repetirla.
Los mensajes pendientes tras un cierre del API deben revisarse en SMTP antes de repetir.

La vista tambien admite huellas SHA-256 de certificados **publicos de DIAN**, obtenidas
por un administrador de una fuente oficial y verificadas fuera del mensaje recibido.
No son el certificado comprado del emisor. Nunca copiar automaticamente una huella
de una respuesta desconocida para hacerla confiable. Para un `.cer` en formato DER:

```powershell
(Get-FileHash -Algorithm SHA256 -LiteralPath 'C:\certificados\dian-publico.cer').Hash.ToLowerInvariant()
```

## ApplicationResponse y contenedor

El verificador, sin red, exige raiz ApplicationResponse, un DocumentResponse, referencia
al numero y CUFE/CUDE esperados, una firma RSA SHA-2, clave de al menos 2048 bits,
certificado vigente con uso de firma y cobertura del documento completo. Bloquea DTD,
entidades, referencias externas, identificadores duplicados y transformaciones no admitidas.
Una firma integra con certificado embebido sigue **sin confianza** hasta coincidir con
una huella configurada. Se muestra integridad, correlacion y confianza por separado.
La confianza por huella es explicita; no se simula verificacion de revocacion ni validacion
de una cadena ONAC. No reemplaza los requisitos del certificado real del emisor.

Un resultado positivo del transporte real solo puede acreditar aceptacion si existe
ApplicationResponse integro, correlacionado, certificado confiable y ResponseCode `02`.
Si falta algo, queda incierto y conserva evidencia; no se convierte en aceptado por HTTP 200.
La simulacion conserva siempre sus etiquetas locales, aun con certificados de prueba.

El ZIP incluye XML original, PDF, respuestas disponibles y reporte de verificacion.
Si existe ApplicationResponse positivo, firmado y correlacionado, se genera AttachedDocument
con ambos XML embebidos exactamente como texto, datos fiscales y firma XAdES local usando
el `.p12` de PRUEBAS existente. Se valida con el XSD local. El contenedor tambien lleva
marca de PRUEBAS y no acredita expedicion. Sin respuesta valida se incluye un aviso;
no se inventa un ApplicationResponse para completar el paquete.

El anexo 1.9 contiene una inconsistencia de extensiones: la tabla de contenedor menciona
2..N, mientras el ejemplo simplificado omite extensiones. El generador usa una extension
de firma, admitida por el XSD. La conformidad final con DIAN se comprobara en habilitacion;
la validacion local no se presenta como aceptacion oficial.

## API y base de datos

Rutas bajo `/api/v1/facturacion/entrega`:

- `GET/PUT /configuracion`: ADMIN, guardado con version optimista.
- `GET /documentos/{id}`: correo sugerido, historial y validaciones.
- `GET /documentos/{id}/pdf?paper=A4|MM80|MM58`.
- `GET /documentos/{id}/paquete?paper=A4|MM80|MM58`.
- `POST /documentos/{id}/correo`: requestId UUID, recipient, paper.
- `GET /correos/{id}/paquete`: ZIP exacto utilizado en una entrega.

Lectura exige VENTAS_VIEW y correo VENTAS_EDIT. V20261119.4 agrega tablas de configuracion y
evidencia de correo con triggers contra alteracion del paquete y destinatario. No cambia
ventas, cierres, numeraciones, clientes ni credenciales Dataico.

Flyway aplica las migraciones al actualizar el API. Para instalacion manual estan
`scripts/sql/V20261119_4__entrega_documentos_fiscales.sql` y el consolidado
`scripts/sql/consolidado_fiscal_20261119_seguro.sql` (requiere esquema hasta V23).
Son alternativas a Flyway: coordinar su historial, no ejecutar ambos caminos sin control.
Se requieren API y escritorio actualizados.

La numeracion fiscal se separo de la V24 ya existente en master. Antes de actualizar una
base que uso las antiguas V24-V27 fiscales, seguir [compatibilidad del historial](migraciones-fiscales-compatibilidad.md).

## Respaldo y despliegue

Respaldar PostgreSQL y las claves del servidor es necesario para recuperar certificados
y contraseñas cifrados. El instalador del escritorio no incluye ni reemplaza estas claves.

`scripts/backup-fiscal-secrets.ps1` requiere PowerShell 7.4 o superior. Usa AES-256-GCM,
PBKDF2-SHA256 con 600000 iteraciones y contraseña interactiva, sin escribir una copia
intermedia de las claves sin cifrar. Incluye fiscal.key, delivery.key y dataico-token.key.
Si se configuraron rutas personalizadas, indicar la carpeta correcta y verificar los nombres.

```powershell
pwsh -File .\scripts\backup-fiscal-secrets.ps1 -Mode Backup -BackupFile 'D:\respaldos\claves-pos.posbkp'
pwsh -File .\scripts\backup-fiscal-secrets.ps1 -Mode Restore -BackupFile 'D:\respaldos\claves-pos.posbkp' -SecretsDirectory 'D:\restauracion-nueva\secrets'
```

La restauracion rechaza sobrescribir claves y verifica autenticidad del archivo antes
de extraerlo. Conservar la contraseña separadamente y dar acceso a la carpeta restaurada
solo a la cuenta que ejecuta el API. Estos archivos no incluyen el dump de PostgreSQL.

Instalador (Java 25; el comando no modifica la base ni instala automaticamente):

```powershell
.\scripts\build-desktop-installer.ps1 -Type exe
```

## Verificacion y cierre pendiente

Pruebas: firma y alteracion de ApplicationResponse, correlacion y huellas; AttachedDocument
para factura/NC/ND, XSD y firma local; PDF A4/80/58, QR y paginacion; SMTP local real con
MIME y Message-ID; PostgreSQL desechable con concurrencia, cifrado, idempotencia y errores;
JavaFX con teclado, estados y anchos distintos; respaldo/restauracion con claves ficticias.
No se realizaron envios oficiales, correos a terceros ni cambios en la base del usuario.

Resultado: **192 pruebas correctas**, cero fallos, errores u omisiones: 60 del nucleo,
96 del API (26 con PostgreSQL desechable) y 36 del escritorio. Se verificaron ademas
ApplicationResponse y AttachedDocument con `lxml`/`cryptography`, independientemente
de Java XML DSig. API empaquetada e instalador EXE generado; no instalado automaticamente.
La comprobacion de impresion cubre formato y escala; requiere la prueba fisica con la
impresora del equipo destino. Se revisaron visualmente PDF y vistas JavaFX en dos anchos.
La prueba de recorrido completo utiliza una respuesta firmada ficticia y SMTP loopback:
verifica el AttachedDocument, el MIME y que el ZIP recibido coincide con el guardado.
Tambien se restauro un dump PostgreSQL en otra base desechable, verificando conteos y
huellas de documentos, paquetes de correo e historial. No se toco una base del usuario.

Quedan para la ultima etapa: certificado real del emisor, confianza/identidad/revocacion
de ese certificado, ejecucion del TestSetId oficial, correcciones de interoperabilidad
que revele DIAN y activacion de la emision directa en produccion. Las operaciones especiales
fuera del alcance POS actual siguen requiriendo desarrollo propio cuando se soliciten.

Fuentes: [Anexo DIAN 1.9](https://www.dian.gov.co/impuestos/factura-electronica/Documents/Anexo-Tecnico-Factura-Electronica-de-Venta-vr-1-9.pdf),
§6.4, §6.5 y §11.7; [correo con Spring](https://docs.spring.io/spring-framework/reference/integration/email.html),
[PDFBox](https://pdfbox.apache.org/3.0/migration.html) y [ZXing](https://github.com/zxing/zxing).
