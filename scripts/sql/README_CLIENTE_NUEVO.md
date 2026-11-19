# Primera instalación de un cliente

Usar `posdesktop_cliente_nuevo.sql` únicamente en una base PostgreSQL vacía, con la API detenida. Este archivo es una instalación manual del esquema actual hasta V24. No contiene ventas, usuarios de otros negocios, credenciales Dataico ni consecutivos de pruebas.

1. Crear la base `posdesktop` y su usuario de conexión si no existen. En Docker, el compose ya los crea; no repetir la creación. El puerto publicado actual es 15432.
2. Editar los tres valores de `INSERT INTO instalacion_admin` al principio del archivo: usuario, nombre y clave de acceso al POS. La clave debe ser propia (12 caracteres como mínimo; máximo 72 bytes UTF-8). Si incluye una comilla simple, duplicarla dentro del literal SQL.
3. Ejecutar todo el archivo conectado a `posdesktop` como propietario de la base, con permiso para instalar pgcrypto. En psql usar `-v ON_ERROR_STOP=1`. En pgAdmin ejecutar sin envolver el archivo en otra transacción. Debe terminar en COMMIT.
4. Antes de abrir el aplicativo, configurar `spring.flyway.enabled: false` en el YAML que utiliza la API. Con el lanzador instalado es `%USERPROFILE%/posdesktop-media/config/application.yml`; si aún no existe, preparar esa configuración antes del primer arranque o definirla en el YAML fuente antes de generar el instalador. En el arranque directo de la API desde el IDE se utiliza su YAML de recursos salvo que se configure uno externo.

No mezclar esta instalación manual con el full-init antiguo, el consolidado de producción ni las migraciones históricas por separado. Este archivo no crea ni modifica el historial Flyway. Las futuras actualizaciones de una instalación manual requieren SQL revisado específicamente para ella.

MongoDB se instala por separado. Con la luz electrónica apagada no hace falta habilitar Dataico. Si posteriormente se habilita, registrar los datos reales de la nueva persona y la dirección del consumidor final desde el POS: no se precargan credenciales, dirección ni numeración de otro negocio.

Validación realizada en una base temporal aislada: creación del esquema, hash BCrypt verificable, un administrador, cero ventas y consecutivos, Dataico deshabilitado y rechazo de una segunda ejecución. La base de datos real no se modificó.
