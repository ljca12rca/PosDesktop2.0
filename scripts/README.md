# Scripts de apoyo

La raiz del repositorio ahora funciona como `pos-root` y esta separada en:

```text
pos-root
  /pos-domain
  /pos-api
  /pos-desktop
  /scripts
```

## Infraestructura local

Levanta PostgreSQL, MongoDB y el API compilado desde este repositorio:

```powershell
.\scripts\dev-up.ps1
```

Ese comando usa dos archivos:

```text
scripts/docker-compose.yml
  pensado para despliegue consumiendo una imagen publicada

scripts/docker-compose.local-build.yml
  override local para construir el API desde el repo durante desarrollo
```

Servicios incluidos:

```text
API
  host: localhost
  port: 8083
  health: /actuator/health

PostgreSQL
  host: localhost
  port: 15432
  database: posdesktop
  user: pos
  password: pos123

MongoDB
  host: localhost
  port: 27018
  database: posdesktop_media
  user: pos_app
  password: pos123
  authSource: posdesktop_media
```

El API ejecutado desde el IDE usa el mismo puerto PostgreSQL publicado por Docker:
`15432` por defecto. Para otro puerto, define `POS_PG_PORT` tanto al iniciar Compose
como en la configuracion de ejecucion del IDE. `POS_PG_URL` permite indicar una URL JDBC
completa y tiene prioridad. Dentro de Docker el API sigue usando `postgres:5432`.

El compose crea:

```text
- contenedor posdesktop-api con reinicio automatico
- base relacional posdesktop
- base documental posdesktop_media
- acceso Mongo del API usando el usuario root configurado en Docker
- volumen persistente para documentos de facturas y soportes
```

## Despliegue en otro computador

Para no depender del repositorio completo, el archivo principal `scripts/docker-compose.yml`
consume la imagen publicada del API en GHCR:

```text
ghcr.io/ljca12rca/posdesktop-api:latest
```

Con eso, en otra maquina solo necesitas:

```text
- Docker Desktop
- el archivo docker-compose.yml
```

Comando de despliegue:

```powershell
docker compose -f docker-compose.yml up -d
```

Ese despliegue incluye `watchtower`, que revisa automaticamente si existe una version nueva
de la imagen del API en GHCR y, si detecta una nueva, recrea solo el contenedor `api`
manteniendo la misma configuracion.

Comportamiento esperado:

```text
- haces push a master
- GitHub Actions publica ghcr.io/ljca12rca/posdesktop-api:latest
- watchtower detecta la nueva imagen
- el contenedor api se reinicia con la version actualizada
```

La revision automatica esta configurada cada 60 segundos.

## Publicacion automatica de la imagen

Se agrego el workflow:

```text
.github/workflows/publish-api-image.yml
```

Publica el API a GHCR cuando haces push a `master` o cuando lo ejecutas manualmente desde GitHub Actions.

Imagen publicada:

```text
ghcr.io/ljca12rca/posdesktop-api:latest
ghcr.io/ljca12rca/posdesktop-api:sha-<commit>
```

Detiene la infraestructura:

```powershell
.\scripts\dev-down.ps1
```

## Ejecutar la API por fuera de Docker

Cuando Maven este disponible en la maquina:

```powershell
.\scripts\run-api.ps1
```

Comando equivalente:

```powershell
mvn -pl pos-api spring-boot:run
```

La API expone por defecto en `http://localhost:8083`.

## Ejecutar el POS Desktop

```powershell
.\scripts\run-desktop.ps1
```

Comando equivalente:

```powershell
mvn -pl pos-desktop javafx:run
```

## Generar instalador del POS Desktop

Genera un instalador de Windows para la interfaz del POS:

```powershell
.\scripts\build-desktop-installer.ps1
```

Opciones disponibles:

```powershell
.\scripts\build-desktop-installer.ps1 -Type exe
.\scripts\build-desktop-installer.ps1 -Type msi
.\scripts\build-desktop-installer.ps1 -Type app-image
```

Detalles del flujo:

```text
- descarga Maven si no esta instalado
- descarga WiX si no esta instalado y el tipo es exe o msi
- compila pos-desktop
- copia dependencias runtime
- ejecuta jpackage
```

Salida esperada:

```text
build/windows-installer/<fecha-id>/dist
```

Cada empaquetado usa una carpeta nueva fuera de `target`, para conservar instaladores
anteriores y permitir compilar aunque un `.exe` este abierto o sea de solo lectura.

## Empaquetado por modulo

Compilar todo el proyecto:

```powershell
.\mvnw.cmd clean verify
```

Empaquetar solo la API:

```powershell
.\mvnw.cmd -pl pos-api -am clean package
```

Empaquetar solo el desktop:

```powershell
.\mvnw.cmd -pl pos-desktop -am clean package
```

El wrapper incluido fija Maven 3.9.9; no requiere instalar Maven en el PATH. Requiere
JDK 25 en `JAVA_HOME`. En el IDE selecciona el wrapper del proyecto y JDK 25 para Maven.
Los instaladores antiguos de `pos-desktop/target/installer` se conservan durante `clean`;
las clases, recursos, JAR y reportes de prueba se siguen limpiando normalmente.

La migracion inicial de la API corre automaticamente con Flyway al arrancar `pos-api`.
