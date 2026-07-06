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

Levanta PostgreSQL y MongoDB:

```powershell
.\scripts\dev-up.ps1
```

Servicios incluidos:

```text
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
- base relacional posdesktop
- base documental posdesktop_media
- usuario de aplicacion Mongo pos_app
- coleccion inicial documentos_soporte
```

Detiene la infraestructura:

```powershell
.\scripts\dev-down.ps1
```

## Ejecutar la API

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
