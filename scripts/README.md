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
  port: 55432
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
pos-desktop/target/installer/dist
```

## Empaquetado por modulo

Compilar todo el proyecto:

```powershell
mvn clean package
```

Empaquetar solo la API:

```powershell
mvn -pl pos-api -am clean package
```

Empaquetar solo el desktop:

```powershell
mvn -pl pos-desktop -am clean package
```

La migracion inicial de la API corre automaticamente con Flyway al arrancar `pos-api`.
