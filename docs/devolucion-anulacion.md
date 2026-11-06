# Devolucion y Anulacion

## Experiencia acordada para la implementacion

Una pantalla con una tarea principal: preparar un ajuste sobre una factura existente.
La factura determina el proveedor; nunca se cambia Dataico por DIAN directa al ajustar.

1. Buscar por numero o cliente, Enter selecciona el primer resultado; flechas cambian resultado.
2. Elegir 1 Devolucion, 2 Anulacion o 3 Ajustar valor. Las dos primeras son credito fiscal;
   la tercera es debito fiscal, nunca se denomina anulacion a un debito.
3. Tabla con articulo, cantidad original, disponible, cantidad a devolver e incremento unitario.
   Enter edita, Enter confirma; Escape cancela edicion y las flechas recorren filas.
   Se admiten cifras del teclado numerico y coma o punto decimal, sin separadores de miles.
4. Revisar motivo, observacion y resumen antes de guardar. La anulacion usa todos los
   articulos y exige confirmacion explicita. No se modifica ni elimina la factura original.
5. Historial de ajustes con evidencia y acciones disponibles segun estado/proveedor.
   El envio a Dataico requiere confirmacion explicita. Los borradores DIAN siguen sin transporte.

Atajos visibles: F2 buscar, F3 operacion, F4 tabla, F6 revisar, F8 guardar,
F9 enviar seleccionado. Tab y Shift+Tab conservan navegacion convencional.
No interceptar cifras mientras el usuario escribe en campos. No usar '/' (atajo global del POS).

Criterios UX: labels persistentes, proveedor/ambiente visibles, total recalculado por servidor,
errores recuperables con foco, estados cargando/vacio/error/exito, sin botones cortados a 760px,
ninguna accion fiscal al cambiar seleccion, y ninguna confirmacion disparada por Enter heredado.

## Arquitectura

Reglas y calculos inmutables en pos-invoicing, persistencia y orquestacion transaccional en
pos-api, contrato de proveedor para Dataico y generacion XML DIAN, vista y transporte HTTP
separados en pos-desktop. Snapshot de origen, numeracion independiente, reservas de cantidades
y clave de idempotencia evitan dobles ajustes. No se modifica la logica de emision de ventas.

## Uso desde el POS

Con la luz encendida, abrir **Devolucion y Anulacion** en el menu general.

- Buscar una factura por su numero (tambien una parte numerica) o por cliente. Enter carga
  el unico resultado o permite escoger con flechas cuando hay varios.
- En Operacion, digitar **1** para devolver articulos, **2** para anular la factura completa
  o **3** para aumentar un valor. Enter pasa a los articulos.
- En la tabla, `+` suma una unidad, `-` resta y `*` toma las cantidades disponibles. Enter
  permite escribir una cantidad; en Ajustar valor tambien se puede recorrer las columnas
  con las flechas y editar el incremento unitario. Este es el valor adicional, no el precio nuevo.
- Coma y punto se aceptan como separador decimal; no usar separadores de miles. Enter confirma
  la celda y avanza; Escape revierte. Al terminar se enfoca el motivo.
- Enter en el motivo revisa el ajuste (Shift+Enter permite un salto de linea). Se muestran base,
  impuestos y total calculados en el servidor. Enter en Guardar crea el borrador. La anulacion
  requiere confirmar: flechas izquierda/derecha eligen Confirmar o Cancelar y Enter ejecuta.
- En el historial, F5 enfoca filas, flechas seleccionan, F10 abre la evidencia y F9 solicita
  envio a Dataico con otra confirmacion. Tambien hay consulta de estado, PDF y descarte.
- Para DIAN directa, Ver documento permite **Firmar copia de prueba**, abrir la copia firmada,
  verificarla y exportar el XML. Sigue siendo evidencia local, sin transporte DIAN.

Los datos originales, el proveedor y el ambiente permanecen visibles. El usuario no reingresa
cliente, precios originales ni impuestos. No hay envio automatico al guardar ni al cambiar de fila.

### Numeracion

Un administrador configura con F7 los prefijos y siguientes consecutivos por cuenta, ambiente
y proveedor. Devolucion y Anulacion comparten una numeracion; Ajustar valor tiene otra.
Para Dataico usar la numeracion real de **Ventas > Notas Credito / Notas Debito > Numeraciones**.
Se reutilizan token, cuenta, URL y timeouts de la configuracion Dataico existente. El JSON lleva
`numbering.prefix` y `flexible=true`, conforme a los ejemplos oficiales de notas referenciadas.
No se usa ni altera el consecutivo de ventas o de facturas electronicas.

El numero se reserva al guardar el borrador. No se recupera al descartarlo. Un fallo seguro permite
reintentar el mismo snapshot y numero; un timeout u otro resultado incierto exige consultar por GET.
Un documento recibido no habilita otro POST, aunque falle una consulta posterior.

## Persistencia, controles y permisos

V25 agrega `numeraciones_ajustes` y `ajustes_fiscales`, sin cambiar ventas ni credenciales.
Los documentos y eventos usan la evidencia inmutable de V24. El ajuste conserva factura original,
huella, detalle, cantidades, impuestos, motivo y solicitud nativa Dataico cuando corresponde.
Un trigger impide modificar el snapshot o eliminar el ajuste; solo cambia su ciclo de procesamiento.

Guardar bloquea configuracion DIAN (si corresponde), venta y numeracion en ese orden. Las cantidades
de borradores, envios inciertos y documentos recibidos se reservan; descartar un borrador o rechazo
seguro las libera. Una anulacion previa bloquea otros ajustes; no se anula una factura que ya tiene
otros ajustes para evitar revertir dos veces los mismos valores. La solicitud lleva UUID de idempotencia.

VENTAS_VIEW consulta; VENTAS_EDIT prepara, guarda y solicita acciones; solo ADMIN configura numeros.
El registro en Dataico y su estado DIAN se muestran por separado. La evidencia general tambien conserva
el estado real del ajuste, mediante una consulta conjunta para las filas del listado.

Este modulo registra documentos fiscales. **No realiza reintegros automaticos de dinero, movimientos
de caja ni modifica cierres o la venta original.** La disponibilidad se calcula con los ajustes
registrados en POSDesktop; no sincroniza notas creadas externamente en Dataico.

## Cobertura y limites explicitos

- Devolucion: credito, concepto DIAN 1, Dataico `DEVOLUCION`.
- Anulacion: credito, concepto DIAN 2, Dataico `ANULACION`, detalle completo original.
- Ajustar valor: debito, concepto DIAN 3, Dataico `CAMBIO_VALOR`, solo incremento indicado.
- XML CreditNote / DebitNote, operaciones referenciadas 20 / 30, CUDE-SHA384 con PIN del software,
  referencia a numero, CUFE y fecha originales, tributos y totales, y firma XAdES local existente.
- Dataico consulta el UUID original y exige factura aceptada por DIAN. Obtiene sus items e impuestos
  del proveedor; no de una venta editada. Se comprueba que el detalle coincida con el total local.
  Si faltan impuestos que hagan cuadrar ese total, se bloquea en vez de inferirlos.
- Se admite el perfil porcentual Dataico IVA, INC, ICA e ICUI. Otros tributos requieren un adaptador
  especifico. En DIAN se conservan los tributos del perfil existente. Facturas con ajustes globales,
  retenciones o redondeo explicito requieren distribuirlos antes de habilitar su devolucion; se informa
  ese limite, no se genera un XML incompleto. Si un redondeo acumulado excede el total, se bloquea.
- DIAN directa solo permite borradores de PRUEBAS, sin transmision ni aceptacion simulada. La firma
  mantiene `trustVerified=false` y `readyToTransmit=false`; certificado real y habilitacion pendientes.
- No se hicieron operaciones reales en Dataico o DIAN. La validacion de extremo a extremo con la cuenta
  de pruebas debe hacerse una vez revisada su numeracion. No se generaron instaladores ni despliegues.

## Fuentes verificadas

[Anexo DIAN 1.9](https://www.dian.gov.co/impuestos/factura-electronica/Documents/Anexo-Tecnico-Factura-Electronica-de-Venta-vr-1-9.pdf):
estructuras de notas, secciones 11.4.3–11.4.6 y catalogos de motivos. Se valida con los XSD locales
CreditNote y DebitNote y la correccion `coID2Type` ya documentada en `dian19-rules.md`.

La concatenacion de debito publicada en pagina 669 produce SHA-384
`3fa73a86d57d9341c536afde1f85c4efd9d4591c2c22bce4dfb0e6b0d2e83b8f047a8bde7098292e9d2493e60d1c31da`;
el hash impreso `b948...` no corresponde a esa cadena. No se cambia la formula para reproducirlo.
El vector de credito de pagina 666 si coincide. Las pruebas distinguen ambos casos.

Dataico: [crear credito](https://portaldelcliente.dataico.com/es/knowledge/crear-nota-cr%C3%A9dito),
[crear debito](https://portaldelcliente.dataico.com/es/knowledge/crear-nota-d%C3%A9bito),
ejemplos JSON basicos enlazados por esas paginas y catalogos del cliente publico de Dataico, revisados
el 2026-11-06. POST a `/credit_notes` o `/debit_notes` con su envoltura, `invoice_id`, cuenta y motivo;
se mantienen `Auth-token`, `Dataico_account_id` y `x-on-behalf-of` de la integracion existente.

## Instalacion y verificacion

Actualizar API y cliente. Flyway aplica V25; para instalacion manual existe
`scripts/sql/V25__devolucion_anulacion.sql` (requiere V24). El script es aditivo y repetible;
no es necesario ejecutar ambos mecanismos manualmente.

Pruebas: XML y firmas para los dos tipos de nota; CUDE; devolucion parcial, anulacion total e incremento;
autenticacion y contratos HTTP simulados Dataico; limites de cantidades, idempotencia, concurrencia real
PostgreSQL, snapshot inmutable, descarte, timeout, consulta y reintento seguro; acceso administrativo;
teclado numerico, Enter, confirmacion por flechas y capturas JavaFX amplia/estrecha.

Resultado: **163 pruebas correctas, cero fallos, errores u omisiones**: 50 del nucleo,
82 de API (17 contra PostgreSQL desechable) y 31 de escritorio. API y escritorio empaquetados.
Las firmas de CreditNote y DebitNote tambien se comprobaron con un motor independiente
`lxml` + `cryptography`: RSA-SHA256 y las tres referencias coinciden.
El SQL manual se ejecuto dos veces en la base desechable y su cuerpo coincide con la migracion.
Se inspeccionaron las capturas amplia y estrecha, sin botones o campos recortados.
