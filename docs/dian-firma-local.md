# Punto 12: firma XAdES y verificacion local

Implementado en `codex/dian-direct-base-xml`, sin adquirir certificados ni llamar a DIAN/Dataico.
La firma es criptografica real sobre XML; el certificado autofirmado de desarrollo no acredita
identidad fiscal ni sirve para la habilitacion. No existe transporte en esta etapa.

## Uso desde el POS

1. Con Java instalado, ejecutar desde la raiz del proyecto:

   ```powershell
   .\scripts\new-dian-test-certificate.ps1
   ```

   Solicita una contrasena sin mostrarla. Genera un PKCS#12 RSA de 3072 bits, valido 90 dias,
   en `pos-invoicing/target/test-certificate/posdesktop-local-test.p12` (ignorado por Git).
   No sobrescribe archivos existentes. No incluir el certificado privado en el instalador.
2. Con usuario ADMIN y luz encendida, abrir **Configuracion electronica**, ambiente **PRUEBAS**.
   Cargar el `.p12`, su contrasena y guardar. Se usa el almacenamiento cifrado existente;
   la API no devuelve certificado privado ni contrasena.
3. Generar un borrador de una venta con datos fiscales completos. En **Documentos fiscales**,
   seleccionar el borrador y abrir **Ver evidencia > Firmar copia de prueba**.
4. La tabla tendra una nueva evidencia **Firmado local · sin envio**. Abrirla permite
   **Verificar firma local** y **Guardar XML**. El original permanece intacto.

VENTAS_EDIT permite firmar; VENTAS_VIEW permite verificar. La firma de esta etapa esta
bloqueada para PRODUCCION y para documentos Dataico. No se alteran ventas, cierres,
consecutivos ni estados de aceptacion. No requiere otra migracion ademas de V24.

## Perfil implementado y fuentes

- XMLDSig enveloped, XAdES-EPES v1.3.2, segunda UBLExtension.
- RSA-SHA256, SHA-256 y Canonical XML 1.0 sin comentarios.
- Tres referencias: documento completo, KeyInfo y SignedProperties; referencias internas
  exclusivamente. Identificadores duplicados, firmas multiples y transformaciones ajenas
  al perfil se rechazan. Parser sin DTD, entidades externas ni consultas de red.
- SigningTime con offset colombiano; SigningCertificate con digest, emisor y serial
  de cada certificado incluido; KeyInfo con cadena disponible en el PKCS#12.
- Politica explicita, rol `supplier` y DataObjectFormat `text/xml`.
- Se exige una unica identidad RSA >=2048 bits, clave privada correspondiente al certificado,
  uso de firma, vigencia y coherencia criptografica de la cadena proporcionada.
- Se vuelve a parsear el XML serializado y se verifican firma y todas sus referencias.
  Cualquier modificacion de contenido firmado invalida la firma.

Fuente principal: [anexo tecnico DIAN 1.9](https://www.dian.gov.co/impuestos/factura-electronica/Documents/Anexo-Tecnico-Factura-Electronica-de-Venta-vr-1-9.pdf),
secciones 10.5–10.12 (paginas 641–646), publicado en
[Documentacion tecnica oficial](https://micrositios.dian.gov.co/sistema-de-facturacion-electronica/documentacion-tecnica/).

Politica enlazada: [politica de firma v2](https://facturaelectronica.dian.gov.co/politicadefirma/v2/politicadefirmav2.pdf).
Descarga comprobada el 2026-10-30, SHA-256 de sus bytes:
`74ca0cbed706e5a233818a34b48b1241e5490439d49df48e7c1a715eb9a8af46`.
El hash se fija en codigo; firmar no descarga contenido ni acepta un hash recibido del cliente.
Para actualizarlo se debe revisar el documento oficial y repetir las pruebas.

## Persistencia y limites

Cada copia es un nuevo registro BORRADOR, formato `DIAN_SIGNED_LOCAL_V1`.
El snapshot incluye ID y hash del original, snapshot fiscal original y huella del certificado
publico; nunca secretos. La huella de evidencia cubre snapshot y XML firmados. Se registran
eventos en original y copia dentro de la misma transaccion.

`valid=true` significa integridad y perfil de firma locales. Siempre se devuelve
`trustVerified=false` y `readyToTransmit=false`. No se han implementado comprobacion de
confianza ONAC mediante anclas externas, OCSP/CRL, autorizacion del firmante frente al emisor,
ni pruebas de aceptacion DIAN. Esas comprobaciones requieren el certificado real y completar
la validacion final antes de permitir produccion. Una cadena incluida no es una cadena confiable
por el mero hecho de venir en el XML.

La validacion XSD continua usando la correccion local documentada de `coID2Type`;
ver `dian19-rules.md`. No se presenta como aceptacion del XSD original contradictorio.

API:

- `POST /api/v1/facturacion/documentos/{id}/firma-local`
- `GET /api/v1/facturacion/documentos/{id}/firma-local/validacion`

El siguiente incremento es el punto 13: empaquetado y transporte. El certificado real puede
adquirirse despues del desarrollo local, antes de las pruebas oficiales.

## Verificacion del incremento

141 pruebas correctas: 43 del nucleo, 69 de API (12 con PostgreSQL desechable) y 29 del
escritorio; cero errores, fallos u omisiones. API y escritorio empaquetados con Maven.
Las pruebas cubren firma tras serializar/reparsear, cambios de importes/descripciones,
propiedades firmadas y certificado, contrasena incorrecta, expiracion, firma duplicada,
referencias externas y XXE; bloqueo de produccion/Dataico, evidencia corrupta y certificado
faltante; copia inmutable desde snapshot sin consumir consecutivos ni emitir, y flujo JavaFX.

Ademas se verifico el XML generado con un motor independiente (`lxml` + `cryptography`):
Canonical XML inclusivo, RSA-SHA256 y los tres digests coinciden. Se ejecuto el script generador
con un certificado desechable y se inspecciono visualmente la ventana de evidencia.

Comando de prueba completa (solo contra el PostgreSQL de prueba descrito en el documento 7–11):

```powershell
.\.tools\apache-maven-3.9.9\bin\mvn.cmd '-Dpos.fiscal.test.url=jdbc:postgresql://127.0.0.1:55439/posdesktop_fiscal_test' package
```

No se construyo instalador ni se desplego en produccion. Para instalar esta version se
requieren API y cliente actualizados, mas V24 si aun no esta aplicada.
