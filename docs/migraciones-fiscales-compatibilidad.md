# Numeracion fiscal compatible con master

`master` ya tiene `V24__sugerencias_articulos.sql`. Se conserva ese archivo exactamente
como esta en master. Las migraciones de este desarrollo se separan para no reutilizar
V24 ni chocar con versiones ordinarias futuras:

| Nombre antiguo de la rama fiscal | Version actual |
| --- | --- |
| V24 persistencia/configuracion DIAN | V20261119.1 |
| V25 Devolucion y Anulacion | V20261119.2 |
| V26 transporte DIAN | V20261119.3 |
| V27 entrega PDF/correo | V20261119.4 |

Los cuerpos SQL y sus checksums se conservan: solo cambia la version y nombre del archivo.
Los documentos anteriores que mencionan V24-V27 fiscales describen la numeracion inicial.
V24 sugerencias no agrega logica comercial a esta rama: amplia nombre de articulo y crea
los indices ya existentes en master para mantener compatibilidad de esquema.

## Instalacion normal desde master

Con V24 sugerencias aplicada, actualizar el API permite a Flyway aplicar las cuatro
versiones 20261119.x. No ejecutar repair ni el puente de historial para este caso.
Tambien se prueba instalacion limpia desde V1, preservando historia de ventas anterior.

## Si ya se ejecuto esta rama con V24-V27 fiscales antiguas

Antes de actualizar, respaldar PostgreSQL y claves del API. Detener el API y ejecutar
manualmente `scripts/sql/compatibilidad_historial_fiscal_pre_20261119.sql`.

El puente bloquea el historial, acepta exclusivamente nombres/checksums conocidos y
operaciones exitosas, cambia solo version/script de las migraciones fiscales, incorpora
la V24 original de master con indices repetibles y registra su checksum verificado.
Conserva documentos, eventos, clientes, ventas, numeraciones y secretos. Todo ocurre
en una transaccion. Un historial diferente o mezcla de versiones antiguas/nuevas aborta
sin cambios. Ejecutarlo nuevamente es inocuo. No se usa Flyway repair para ocultar diferencias.

El puente se verifico en PostgreSQL desechable con V24-V27 fiscales aplicadas y con
una segunda ejecucion; despues se verifica la validacion Flyway contra los recursos nuevos.
No se ejecuto sobre ninguna base del usuario.
Tambien se comprobo el rechazo de un historial desconocido sin repararlo y la instalacion
desde una base limpia que aplica primero la V24 de master.

## SQL manual

`scripts/sql/consolidado_fiscal_20261119_seguro.sql` agrega las cuatro estructuras fiscales
sobre un esquema POS hasta V23 o superior. Es repetible, no altera el historial Flyway y
no incluye la V24 sugerencias. Los scripts individuales nuevos estan en la misma carpeta.
Los nombres manuales V24-V27 previos se conservan por compatibilidad con enlaces anteriores.

Preferir Flyway para instalaciones administradas por el API. El consolidado permite
revisar y aplicar cambios manualmente, pero no sustituye el puente si hay un historial
fiscal antiguo. No editar checksums ni marcar migraciones desconocidas como aplicadas.
