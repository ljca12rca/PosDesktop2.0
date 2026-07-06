# Scripts de apoyo

## Infraestructura local

Levanta PostgreSQL y MongoDB:

```powershell
.\scripts\dev-up.ps1
```

Detiene la infraestructura:

```powershell
.\scripts\dev-down.ps1
```

## Aplicacion

Cuando Maven este disponible en la maquina:

```powershell
mvn spring-boot:run
```

La migracion inicial corre automaticamente con Flyway al arrancar la aplicacion.
