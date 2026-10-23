# Módulo de facturación electrónica

El módulo está ubicado en `pos-api` y no tiene controlador, persistencia ni referencias al módulo de ventas. Por tanto, añadirlo no cambia el comportamiento actual del POS.

## Diseño

`FacturacionElectronicaService` es el caso de uso. Depende de `ProveedorFacturacionElectronica`, un puerto que permite incorporar nuevos operadores (por ejemplo, otro proveedor DIAN) sin alterar ventas ni la lógica de aplicación. `DataicoFacturacionElectronicaProvider` es un adaptador que concentra REST, autenticación y el contrato externo. `DataicoInvoiceMapper` traduce el modelo canónico y es el único componente que debe evolucionar cuando Dataico cambie su JSON.

El módulo entrega inicialmente factura de venta, consulta y reenvío. Nota crédito y débito ya existen en el contrato por medio de `TipoDocumentoElectronico`; su mapeo debe implementarse después de acordar el modelo de negocio y las numeraciones autorizadas.

## Activación segura

Por defecto no se crea cliente HTTP ni se envía información a Dataico. Para activar el adaptador, configurar variables de entorno:

```text
POS_DATAICO_ENABLED=true
POS_DATAICO_AUTH_TOKEN=token-entregado-por-dataico
POS_DATAICO_ACCOUNT_ID=identificador-de-cuenta-opcional
POS_DATAICO_BASE_URL=https://api.dataico.com/direct/dataico_api/v2
POS_DATAICO_PREFIX=prefijo-de-la-numeracion
POS_DATAICO_FLEXIBLE=false
```

Se recomiendan inicialmente las credenciales sandbox entregadas por Dataico. Nunca registrar el token en YAML, código fuente ni logs. Los tiempos de conexión y lectura se pueden ajustar con `POS_DATAICO_CONNECT_TIMEOUT` y `POS_DATAICO_READ_TIMEOUT`.

## Pendiente de integración

1. Crear un ensamblador en ventas que transforme una venta cerrada a `DocumentoElectronico` y defina cuándo emitir.
2. Persistir una bitácora/outbox con idempotencia, intentos y la respuesta (UUID, CUFE, QR y estado DIAN).
3. Definir la configuración tributaria, cliente consumidor final, numeraciones y catálogo DIAN por empresa.
4. Exponer endpoints o acciones de interfaz solo cuando se apruebe el flujo funcional.

## Referencias

- [Documentación técnica FE Dataico](https://portaldelcliente.dataico.com/es/knowledge/documentaci%C3%B3n-t%C3%A9cnica-de-la-api-de-dataico-factura-electr%C3%B3nica): `POST /invoices`, `GET /invoices?number=...` y autenticación.
- [Reenvío de factura Dataico](https://portaldelcliente.dataico.com/es/knowledge/reenviar-a-la-dian-una-factura-existente-de-la-cuenta-dataico-con-el-estado-dian_no_enviado): `PUT /invoices/{uuid}` con acciones de envío.
