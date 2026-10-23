# Configuración de facturación electrónica

Acceso: menú **Configuración Dataico**, disponible para el rol ADMIN únicamente con la luz de facturación electrónica encendida.
Al apagar la luz se oculta el acceso y, si la configuración estaba abierta, se vuelve a Ventas.
El carácter `/` alterna el modo desde cualquier control de la ventana principal y de sus diálogos,
con la sesión iniciada y la API disponible. No depende del foco en un textbox.

Campos: Account ID (UUID de la empresa), habilitación de Dataico, prefijo electrónico, resolución, próximo número a utilizar
(no el último usado), ambiente, numeración flexible y nuevo token. Todos son configurables desde el POS.
En **Conexión avanzada** se configura la URL HTTPS del dominio oficial `api.dataico.com`, conexión de 1–10 segundos
y respuesta de 1–30 segundos. Se rechazan otras direcciones para impedir que el token se envíe a terceros.
Los límites de espera conservan margen para el único reintento del backend dentro de los 90 segundos admitidos por el POS.
El token actual nunca se devuelve al POS. El campo es de contraseña: vacío conserva el token actual;
un valor nuevo reemplaza al anterior, se guarda cifrado en `dataico_configuracion.token_cifrado`
y tiene prioridad sobre el YAML. Las llamadas de emisión, consulta DIAN, consulta de facturas y reenvío
usan el token vigente sin reiniciar la API. Guardar un token no verifica su validez contra Dataico ni envía facturas.

Los valores del YAML se usan hasta el primer guardado. Después, los ajustes de cuenta, conexión y numeración de `dataico_configuracion`
tienen prioridad. El próximo número siempre se consulta en `dataico_consecutivos`, por prefijo, sin reiniciarlo.
Cambiar estos datos no modifica el prefijo local VTA ni envía ventas normales a Dataico.
Guardar configuración no envía ninguna factura ni consume un consecutivo.
Los cambios también se aplican a los encabezados `Dataico_account_id` y `x-on-behalf-of`, y al Account ID del JSON de la factura.
Deshabilitar Dataico impide construir la conexión HTTP; reactivarlo no requiere reiniciar la API.

Para desplegar, reiniciar API y POS. La migración V21 crea la tabla sin cambiar la numeración vigente.
Si se administran migraciones manualmente, ejecutar `scripts/sql/V21__configuracion_dataico.sql`
y `scripts/sql/V22__token_dataico_cifrado.sql`, y finalmente `scripts/sql/V23__conexion_dataico_configurable.sql` antes de iniciar la API.
El inicializador garantiza que exista la fila de configuración cuando el esquema es administrado por Hibernate.

La configuración se captura al emitir y mantiene un bloqueo compartido hasta terminar la transacción.
El guardado obtiene un bloqueo exclusivo y comprueba versión y número original: si otra venta avanzó la secuencia,
el administrador debe recargar. No se permite retroceder un consecutivo ya reservado; no hay un máximo fiscal local.
La resolución, ambiente y prefijo viajan en la misma copia inmutable hasta el JSON enviado a Dataico.

El administrador debe comprobar que los ajustes coincidan con la numeración autorizada en su cuenta.
Cambiar PRUEBAS/PRODUCCION no cambia cuenta ni credenciales. Esta pantalla no verifica la autorización ante DIAN.
Cambiar el Account ID no migra las facturas históricas ni convierte esta instalación en multiempresa.
Antes de emitir en producción, usar la base de datos y numeración definitivas: los consecutivos locales siguen reservados
por prefijo y no se permite retrocederlos ni duplicar documentos ya registrados. Los documentos de otra cuenta requieren
sus propias credenciales para consultarlos o reenviarlos.

Referencia de la URL y autenticación: [documentación oficial de Dataico](https://portaldelcliente.dataico.com/es/knowledge/documentaci%C3%B3n-t%C3%A9cnica-de-la-api-de-dataico-factura-electr%C3%B3nica).

## Clave de cifrado y copias de seguridad

Se usa AES-256-GCM con una clave generada en el primer guardado de un token. No hay clave incluida en el código.
En Windows se guarda en `%LOCALAPPDATA%/PosDesktop/secrets/dataico-token.key` del usuario que ejecuta la API;
en otros sistemas, en `~/.posdesktop/secrets/dataico-token.key`. El archivo se restringe al propietario.
Se puede especificar otra ubicación con `pos.facturacion-electronica.dataico.token-key-file` en el YAML.

Respaldar la clave separadamente de la base de datos. Al mover la API a otra máquina/usuario, restaurar también esa clave
o introducir un token nuevo en el POS. La lectura nunca genera una clave sustituta si falta la original.
La pantalla de configuración sigue disponible para reemplazar el token aun cuando se haya perdido la clave.
Hasta guardar un reemplazo se conserva el token del YAML, sin migrarlo ni sobrescribirlo automáticamente.
