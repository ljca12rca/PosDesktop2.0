param(
    [string]$ServerInstance = "LUIS980918\MSSQLSERVER02",
    [string]$Database = "pos-desktop",
    [string]$OutputPath = "$(Split-Path -Parent $PSCommandPath)\migracion_legacy_cierres_separados_abonos.sql"
)

$ErrorActionPreference = "Stop"

$culture = [System.Globalization.CultureInfo]::InvariantCulture

function Invoke-LegacyQuery {
    param(
        [string]$Query
    )

    Invoke-Sqlcmd -ServerInstance $ServerInstance -Database $Database -Query $Query -QueryTimeout 120
}

function To-PgTextLiteral {
    param(
        [AllowNull()]
        [object]$Value
    )

    if ($null -eq $Value) {
        return "NULL"
    }

    $text = $Value.ToString().Replace("'", "''").Replace("`r", " ").Replace("`n", " ")
    return "'$text'"
}

function To-PgDateLiteral {
    param(
        [AllowNull()]
        [object]$Value
    )

    if ($null -eq $Value) {
        return "NULL"
    }

    return "'{0}'" -f ([datetime]$Value).ToString("yyyy-MM-dd", $culture)
}

function To-PgTimestampLiteral {
    param(
        [AllowNull()]
        [object]$Value
    )

    if ($null -eq $Value) {
        return "NULL"
    }

    return "'{0}'" -f ([datetime]$Value).ToString("yyyy-MM-dd HH:mm:ss.fff", $culture)
}

function To-PgNumericLiteral {
    param(
        [AllowNull()]
        [object]$Value
    )

    if ($null -eq $Value) {
        return "0.00"
    }

    return ([decimal]$Value).ToString("0.00", $culture)
}

function To-PgIntegerLiteral {
    param(
        [AllowNull()]
        [object]$Value
    )

    if ($null -eq $Value) {
        return "0"
    }

    return ([int]$Value).ToString($culture)
}

function Append-ValuesBlock {
    param(
        [System.Text.StringBuilder]$Builder,
        [string]$InsertHeader,
        [System.Collections.IEnumerable]$Rows,
        [scriptblock]$RowFormatter
    )

    $materialized = @($Rows)
    if ($materialized.Count -eq 0) {
        $null = $Builder.AppendLine("-- Sin filas para bloque: $InsertHeader")
        return
    }

    $null = $Builder.AppendLine($InsertHeader)
    for ($index = 0; $index -lt $materialized.Count; $index++) {
        $rowSql = & $RowFormatter $materialized[$index]
        if ($index -lt ($materialized.Count - 1)) {
            $null = $Builder.AppendLine("    $rowSql,")
        } else {
            $null = $Builder.AppendLine("    $rowSql;")
        }
    }
    $null = $Builder.AppendLine()
}

$cierres = Invoke-LegacyQuery @"
SET NOCOUNT ON;
SELECT
    CAST(id AS int) AS legacy_id,
    CAST(fecha AS date) AS fecha_operacion,
    CAST(pagoTrabajadoras AS decimal(18,2)) AS pago_trabajadoras,
    CAST(ahorro AS decimal(18,2)) AS ahorro,
    CAST(totalBase AS decimal(18,2)) AS total_base,
    CAST(totalEnCaja AS decimal(18,2)) AS total_en_caja,
    CAST(totalCierre AS decimal(18,2)) AS total_cierre
FROM dbo.Cierres
ORDER BY CAST(fecha AS date), id;
"@

$separados = Invoke-LegacyQuery @"
SET NOCOUNT ON;
SELECT
    CAST(s.id AS int) AS legacy_id,
    LTRIM(RTRIM(s.cliente)) AS cliente,
    LTRIM(RTRIM(s.articulos)) AS articulos,
    CAST(s.costoTotal AS decimal(18,2)) AS costo_total,
    CAST(s.restante AS decimal(18,2)) AS restante,
    CAST(s.fecha AS date) AS fecha_separacion,
    CAST(ISNULL(a.total_abonos, 0) AS decimal(18,2)) AS total_abonos,
    CASE
        WHEN CAST(s.costoTotal - s.restante AS decimal(18,2)) <> CAST(ISNULL(a.total_abonos, 0) AS decimal(18,2))
            THEN CAST(1 AS bit)
        ELSE CAST(0 AS bit)
    END AS tiene_descuadre
FROM dbo.Separadoes s
LEFT JOIN (
    SELECT separado_id, SUM(valorAbono) AS total_abonos
    FROM dbo.Abonoes
    GROUP BY separado_id
) a ON a.separado_id = s.id
ORDER BY s.id;
"@

$abonos = Invoke-LegacyQuery @"
SET NOCOUNT ON;
SELECT
    CAST(a.id AS int) AS legacy_id,
    CAST(a.separado_id AS int) AS legacy_separado_id,
    ROW_NUMBER() OVER (PARTITION BY a.separado_id ORDER BY a.fechaPago, a.id) AS numero_abono,
    CAST(a.fechaPago AS datetime) AS fecha_abono,
    CAST(a.valorAbono AS decimal(18,2)) AS valor_abono
FROM dbo.Abonoes a
ORDER BY a.separado_id, a.fechaPago, a.id;
"@

$builder = New-Object System.Text.StringBuilder
$generatedAt = [datetime]::Now.ToString("yyyy-MM-dd HH:mm:ss", $culture)

$null = $builder.AppendLine("-- Migracion generada automaticamente desde SQL Server a PostgreSQL.")
$null = $builder.AppendLine("-- Origen: $ServerInstance / $Database")
$null = $builder.AppendLine("-- Generado: $generatedAt")
$null = $builder.AppendLine("-- Alcance: cierres legacy, separados legacy y abonos legacy.")
$null = $builder.AppendLine("-- Nota: para que los cierres se visualicen correctamente en PosDesktop 2.0")
$null = $builder.AppendLine("-- se crean ventas sinteticas de respaldo. Los abonos de separados tambien")
$null = $builder.AppendLine("-- generan su propia venta sintetica porque abonos_separado requiere venta_id.")
$null = $builder.AppendLine()
$null = $builder.AppendLine("BEGIN;")
$null = $builder.AppendLine()
$null = $builder.AppendLine("-- Limpieza previa de tablas afectadas por esta migracion.")
$null = $builder.AppendLine("DELETE FROM abonos_separado WHERE TRUE;")
$null = $builder.AppendLine("DELETE FROM detalle_venta WHERE TRUE;")
$null = $builder.AppendLine("DELETE FROM ventas WHERE TRUE;")
$null = $builder.AppendLine("DELETE FROM separados WHERE TRUE;")
$null = $builder.AppendLine("DELETE FROM cierres_diarios WHERE TRUE;")
$null = $builder.AppendLine()
$null = $builder.AppendLine('DO $$')
$null = $builder.AppendLine("BEGIN")
$null = $builder.AppendLine("    IF NOT EXISTS (")
$null = $builder.AppendLine("        SELECT 1")
$null = $builder.AppendLine("        FROM usuarios_sistema")
$null = $builder.AppendLine("        WHERE lower(username) = 'keli'")
$null = $builder.AppendLine("    ) THEN")
$null = $builder.AppendLine("        RAISE EXCEPTION 'No existe el usuario keli en usuarios_sistema. Ejecuta primero el script base de seguridad.';")
$null = $builder.AppendLine("    END IF;")
$null = $builder.AppendLine("END")
$null = $builder.AppendLine('$$;')
$null = $builder.AppendLine()
$null = $builder.AppendLine("CREATE OR REPLACE FUNCTION pg_temp.legacy_uuid(namespace text, legacy_id text)")
$null = $builder.AppendLine("RETURNS uuid")
$null = $builder.AppendLine("LANGUAGE SQL")
$null = $builder.AppendLine("IMMUTABLE")
$null = $builder.AppendLine('AS $$')
$null = $builder.AppendLine("    WITH digest AS (")
$null = $builder.AppendLine("        SELECT md5(namespace || ':' || legacy_id) AS h")
$null = $builder.AppendLine("    )")
$null = $builder.AppendLine("    SELECT (")
$null = $builder.AppendLine("        substr(h, 1, 8) || '-' ||")
$null = $builder.AppendLine("        substr(h, 9, 4) || '-' ||")
$null = $builder.AppendLine("        substr(h, 13, 4) || '-' ||")
$null = $builder.AppendLine("        substr(h, 17, 4) || '-' ||")
$null = $builder.AppendLine("        substr(h, 21, 12)")
$null = $builder.AppendLine("    )::uuid")
$null = $builder.AppendLine("    FROM digest;")
$null = $builder.AppendLine('$$;')
$null = $builder.AppendLine()
$null = $builder.AppendLine("CREATE TEMP TABLE legacy_cierres (")
$null = $builder.AppendLine("    legacy_id INTEGER PRIMARY KEY,")
$null = $builder.AppendLine("    fecha_operacion DATE NOT NULL,")
$null = $builder.AppendLine("    pago_trabajadoras NUMERIC(19,2) NOT NULL,")
$null = $builder.AppendLine("    ahorro NUMERIC(19,2) NOT NULL,")
$null = $builder.AppendLine("    total_base NUMERIC(19,2) NOT NULL,")
$null = $builder.AppendLine("    total_en_caja NUMERIC(19,2) NOT NULL,")
$null = $builder.AppendLine("    total_cierre NUMERIC(19,2) NOT NULL")
$null = $builder.AppendLine(");")
$null = $builder.AppendLine()
$null = $builder.AppendLine("CREATE TEMP TABLE legacy_separados (")
$null = $builder.AppendLine("    legacy_id INTEGER PRIMARY KEY,")
$null = $builder.AppendLine("    cliente VARCHAR(160),")
$null = $builder.AppendLine("    articulos VARCHAR(200),")
$null = $builder.AppendLine("    costo_total NUMERIC(19,2) NOT NULL,")
$null = $builder.AppendLine("    restante NUMERIC(19,2) NOT NULL,")
$null = $builder.AppendLine("    fecha_separacion DATE NOT NULL,")
$null = $builder.AppendLine("    total_abonos NUMERIC(19,2) NOT NULL,")
$null = $builder.AppendLine("    tiene_descuadre BOOLEAN NOT NULL")
$null = $builder.AppendLine(");")
$null = $builder.AppendLine()
$null = $builder.AppendLine("CREATE TEMP TABLE legacy_abonos (")
$null = $builder.AppendLine("    legacy_id INTEGER PRIMARY KEY,")
$null = $builder.AppendLine("    legacy_separado_id INTEGER NOT NULL,")
$null = $builder.AppendLine("    numero_abono INTEGER NOT NULL,")
$null = $builder.AppendLine("    fecha_abono TIMESTAMP NOT NULL,")
$null = $builder.AppendLine("    valor_abono NUMERIC(19,2) NOT NULL")
$null = $builder.AppendLine(");")
$null = $builder.AppendLine()

Append-ValuesBlock -Builder $builder -InsertHeader "INSERT INTO legacy_cierres (legacy_id, fecha_operacion, pago_trabajadoras, ahorro, total_base, total_en_caja, total_cierre) VALUES" -Rows $cierres -RowFormatter {
    param($row)
    "({0}, {1}, {2}, {3}, {4}, {5}, {6})" -f `
        (To-PgIntegerLiteral $row.legacy_id), `
        (To-PgDateLiteral $row.fecha_operacion), `
        (To-PgNumericLiteral $row.pago_trabajadoras), `
        (To-PgNumericLiteral $row.ahorro), `
        (To-PgNumericLiteral $row.total_base), `
        (To-PgNumericLiteral $row.total_en_caja), `
        (To-PgNumericLiteral $row.total_cierre)
}

Append-ValuesBlock -Builder $builder -InsertHeader "INSERT INTO legacy_separados (legacy_id, cliente, articulos, costo_total, restante, fecha_separacion, total_abonos, tiene_descuadre) VALUES" -Rows $separados -RowFormatter {
    param($row)
    $descuadre = if ([bool]$row.tiene_descuadre) { "TRUE" } else { "FALSE" }
    "({0}, {1}, {2}, {3}, {4}, {5}, {6}, {7})" -f `
        (To-PgIntegerLiteral $row.legacy_id), `
        (To-PgTextLiteral $row.cliente), `
        (To-PgTextLiteral $row.articulos), `
        (To-PgNumericLiteral $row.costo_total), `
        (To-PgNumericLiteral $row.restante), `
        (To-PgDateLiteral $row.fecha_separacion), `
        (To-PgNumericLiteral $row.total_abonos), `
        $descuadre
}

Append-ValuesBlock -Builder $builder -InsertHeader "INSERT INTO legacy_abonos (legacy_id, legacy_separado_id, numero_abono, fecha_abono, valor_abono) VALUES" -Rows $abonos -RowFormatter {
    param($row)
    "({0}, {1}, {2}, {3}, {4})" -f `
        (To-PgIntegerLiteral $row.legacy_id), `
        (To-PgIntegerLiteral $row.legacy_separado_id), `
        (To-PgIntegerLiteral $row.numero_abono), `
        (To-PgTimestampLiteral $row.fecha_abono), `
        (To-PgNumericLiteral $row.valor_abono)
}

$null = $builder.AppendLine("WITH keli AS (")
$null = $builder.AppendLine("    SELECT id")
$null = $builder.AppendLine("    FROM usuarios_sistema")
$null = $builder.AppendLine("    WHERE lower(username) = 'keli'")
$null = $builder.AppendLine("    LIMIT 1")
$null = $builder.AppendLine(")")
$null = $builder.AppendLine("INSERT INTO cierres_diarios (")
$null = $builder.AppendLine("    id, fecha_operacion, fecha_hora_cierre, estado, cantidad_ventas,")
$null = $builder.AppendLine("    subtotal_calculado, descuento_calculado, impuesto_calculado, total_calculado,")
$null = $builder.AppendLine("    monto_recibido_calculado, cambio_entregado_calculado, monto_neto_caja_calculado,")
$null = $builder.AppendLine("    base_caja, egresos, trabajadoras, ahorro, total_final, observacion,")
$null = $builder.AppendLine("    responsable_usuario_id, creado_en, actualizado_en")
$null = $builder.AppendLine(")")
$null = $builder.AppendLine("SELECT")
$null = $builder.AppendLine("    pg_temp.legacy_uuid('legacy-cierre', lc.legacy_id::text),")
$null = $builder.AppendLine("    lc.fecha_operacion,")
$null = $builder.AppendLine("    lc.fecha_operacion::timestamp + TIME '23:59:59',")
$null = $builder.AppendLine("    'CERRADO',")
$null = $builder.AppendLine("    1,")
$null = $builder.AppendLine("    lc.total_cierre,")
$null = $builder.AppendLine("    0,")
$null = $builder.AppendLine("    0,")
$null = $builder.AppendLine("    lc.total_cierre,")
$null = $builder.AppendLine("    lc.total_cierre,")
$null = $builder.AppendLine("    0,")
$null = $builder.AppendLine("    lc.total_en_caja,")
$null = $builder.AppendLine("    lc.total_base,")
$null = $builder.AppendLine("    lc.pago_trabajadoras + lc.ahorro,")
$null = $builder.AppendLine("    lc.pago_trabajadoras,")
$null = $builder.AppendLine("    lc.ahorro,")
$null = $builder.AppendLine("    lc.total_en_caja,")
$null = $builder.AppendLine("    'Migrado desde SQL Server dbo.Cierres. Legacy id=' || lc.legacy_id,")
$null = $builder.AppendLine("    keli.id,")
$null = $builder.AppendLine("    lc.fecha_operacion::timestamp + TIME '23:59:59',")
$null = $builder.AppendLine("    lc.fecha_operacion::timestamp + TIME '23:59:59'")
$null = $builder.AppendLine("FROM legacy_cierres lc")
$null = $builder.AppendLine("CROSS JOIN keli")
$null = $builder.AppendLine("ON CONFLICT (fecha_operacion) DO UPDATE SET")
$null = $builder.AppendLine("    fecha_hora_cierre = EXCLUDED.fecha_hora_cierre,")
$null = $builder.AppendLine("    estado = EXCLUDED.estado,")
$null = $builder.AppendLine("    cantidad_ventas = EXCLUDED.cantidad_ventas,")
$null = $builder.AppendLine("    subtotal_calculado = EXCLUDED.subtotal_calculado,")
$null = $builder.AppendLine("    descuento_calculado = EXCLUDED.descuento_calculado,")
$null = $builder.AppendLine("    impuesto_calculado = EXCLUDED.impuesto_calculado,")
$null = $builder.AppendLine("    total_calculado = EXCLUDED.total_calculado,")
$null = $builder.AppendLine("    monto_recibido_calculado = EXCLUDED.monto_recibido_calculado,")
$null = $builder.AppendLine("    cambio_entregado_calculado = EXCLUDED.cambio_entregado_calculado,")
$null = $builder.AppendLine("    monto_neto_caja_calculado = EXCLUDED.monto_neto_caja_calculado,")
$null = $builder.AppendLine("    base_caja = EXCLUDED.base_caja,")
$null = $builder.AppendLine("    egresos = EXCLUDED.egresos,")
$null = $builder.AppendLine("    trabajadoras = EXCLUDED.trabajadoras,")
$null = $builder.AppendLine("    ahorro = EXCLUDED.ahorro,")
$null = $builder.AppendLine("    total_final = EXCLUDED.total_final,")
$null = $builder.AppendLine("    observacion = EXCLUDED.observacion,")
$null = $builder.AppendLine("    responsable_usuario_id = EXCLUDED.responsable_usuario_id,")
$null = $builder.AppendLine("    actualizado_en = EXCLUDED.actualizado_en;")
$null = $builder.AppendLine()
$null = $builder.AppendLine("CREATE TEMP TABLE legacy_cierre_target AS")
$null = $builder.AppendLine("SELECT")
$null = $builder.AppendLine("    lc.legacy_id,")
$null = $builder.AppendLine("    cd.id AS cierre_id,")
$null = $builder.AppendLine("    cd.fecha_operacion")
$null = $builder.AppendLine("FROM legacy_cierres lc")
$null = $builder.AppendLine("JOIN cierres_diarios cd ON cd.fecha_operacion = lc.fecha_operacion;")
$null = $builder.AppendLine()

$null = $builder.AppendLine("INSERT INTO ventas (")
$null = $builder.AppendLine("    id, numero_venta, fecha_venta, estado, origen,")
$null = $builder.AppendLine("    monto_manual_informado, subtotal, descuento_total, impuesto_total, total,")
$null = $builder.AppendLine("    monto_recibido, cambio_entregado, observacion, cierre_diario_id, creado_en, actualizado_en")
$null = $builder.AppendLine(")")
$null = $builder.AppendLine("SELECT")
$null = $builder.AppendLine("    pg_temp.legacy_uuid('legacy-cierre-venta', lc.legacy_id::text),")
$null = $builder.AppendLine("    'VTA-MIG-CIERRE-' || LPAD(lc.legacy_id::text, 10, '0'),")
$null = $builder.AppendLine("    lc.fecha_operacion::timestamp + TIME '23:59:59',")
$null = $builder.AppendLine("    'CERRADA',")
$null = $builder.AppendLine("    'MANUAL',")
$null = $builder.AppendLine("    lc.total_cierre,")
$null = $builder.AppendLine("    lc.total_cierre,")
$null = $builder.AppendLine("    0,")
$null = $builder.AppendLine("    0,")
$null = $builder.AppendLine("    lc.total_cierre,")
$null = $builder.AppendLine("    lc.total_cierre,")
$null = $builder.AppendLine("    0,")
$null = $builder.AppendLine("    'Venta sintetica creada por migracion de cierre legacy ' || lc.legacy_id,")
$null = $builder.AppendLine("    lct.cierre_id,")
$null = $builder.AppendLine("    lc.fecha_operacion::timestamp + TIME '23:59:59',")
$null = $builder.AppendLine("    lc.fecha_operacion::timestamp + TIME '23:59:59'")
$null = $builder.AppendLine("FROM legacy_cierres lc")
$null = $builder.AppendLine("JOIN legacy_cierre_target lct ON lct.legacy_id = lc.legacy_id")
$null = $builder.AppendLine("ON CONFLICT (numero_venta) DO UPDATE SET")
$null = $builder.AppendLine("    fecha_venta = EXCLUDED.fecha_venta,")
$null = $builder.AppendLine("    estado = EXCLUDED.estado,")
$null = $builder.AppendLine("    origen = EXCLUDED.origen,")
$null = $builder.AppendLine("    monto_manual_informado = EXCLUDED.monto_manual_informado,")
$null = $builder.AppendLine("    subtotal = EXCLUDED.subtotal,")
$null = $builder.AppendLine("    descuento_total = EXCLUDED.descuento_total,")
$null = $builder.AppendLine("    impuesto_total = EXCLUDED.impuesto_total,")
$null = $builder.AppendLine("    total = EXCLUDED.total,")
$null = $builder.AppendLine("    monto_recibido = EXCLUDED.monto_recibido,")
$null = $builder.AppendLine("    cambio_entregado = EXCLUDED.cambio_entregado,")
$null = $builder.AppendLine("    observacion = EXCLUDED.observacion,")
$null = $builder.AppendLine("    cierre_diario_id = EXCLUDED.cierre_diario_id,")
$null = $builder.AppendLine("    actualizado_en = EXCLUDED.actualizado_en;")
$null = $builder.AppendLine()
$null = $builder.AppendLine("CREATE TEMP TABLE legacy_cierre_venta_target AS")
$null = $builder.AppendLine("SELECT")
$null = $builder.AppendLine("    lc.legacy_id,")
$null = $builder.AppendLine("    v.id AS venta_id")
$null = $builder.AppendLine("FROM legacy_cierres lc")
$null = $builder.AppendLine("JOIN ventas v ON v.numero_venta = 'VTA-MIG-CIERRE-' || LPAD(lc.legacy_id::text, 10, '0');")
$null = $builder.AppendLine()

$null = $builder.AppendLine("INSERT INTO detalle_venta (")
$null = $builder.AppendLine("    id, venta_id, articulo_id, tipo_detalle, orden, descripcion, cantidad, unidad_medida,")
$null = $builder.AppendLine("    precio_unitario, subtotal, descuento, impuesto, total, referencia_externa, creado_en, actualizado_en")
$null = $builder.AppendLine(")")
$null = $builder.AppendLine("SELECT")
$null = $builder.AppendLine("    pg_temp.legacy_uuid('legacy-cierre-detalle', lc.legacy_id::text),")
$null = $builder.AppendLine("    lcvt.venta_id,")
$null = $builder.AppendLine("    NULL,")
$null = $builder.AppendLine("    'MANUAL',")
$null = $builder.AppendLine("    1,")
$null = $builder.AppendLine("    'Migracion cierre legacy ' || lc.legacy_id,")
$null = $builder.AppendLine("    1.000,")
$null = $builder.AppendLine("    'UND',")
$null = $builder.AppendLine("    lc.total_cierre,")
$null = $builder.AppendLine("    lc.total_cierre,")
$null = $builder.AppendLine("    0,")
$null = $builder.AppendLine("    0,")
$null = $builder.AppendLine("    lc.total_cierre,")
$null = $builder.AppendLine("    'CIERRE-LEGACY-' || lc.legacy_id,")
$null = $builder.AppendLine("    lc.fecha_operacion::timestamp + TIME '23:59:59',")
$null = $builder.AppendLine("    lc.fecha_operacion::timestamp + TIME '23:59:59'")
$null = $builder.AppendLine("FROM legacy_cierres lc")
$null = $builder.AppendLine("JOIN legacy_cierre_venta_target lcvt ON lcvt.legacy_id = lc.legacy_id")
$null = $builder.AppendLine("ON CONFLICT (id) DO UPDATE SET")
$null = $builder.AppendLine("    venta_id = EXCLUDED.venta_id,")
$null = $builder.AppendLine("    tipo_detalle = EXCLUDED.tipo_detalle,")
$null = $builder.AppendLine("    orden = EXCLUDED.orden,")
$null = $builder.AppendLine("    descripcion = EXCLUDED.descripcion,")
$null = $builder.AppendLine("    cantidad = EXCLUDED.cantidad,")
$null = $builder.AppendLine("    unidad_medida = EXCLUDED.unidad_medida,")
$null = $builder.AppendLine("    precio_unitario = EXCLUDED.precio_unitario,")
$null = $builder.AppendLine("    subtotal = EXCLUDED.subtotal,")
$null = $builder.AppendLine("    descuento = EXCLUDED.descuento,")
$null = $builder.AppendLine("    impuesto = EXCLUDED.impuesto,")
$null = $builder.AppendLine("    total = EXCLUDED.total,")
$null = $builder.AppendLine("    referencia_externa = EXCLUDED.referencia_externa,")
$null = $builder.AppendLine("    actualizado_en = EXCLUDED.actualizado_en;")
$null = $builder.AppendLine()

$null = $builder.AppendLine("INSERT INTO separados (")
$null = $builder.AppendLine("    id, numero_separado, fecha_separacion, estado, nombre_cliente, documento_cliente, telefono_cliente,")
$null = $builder.AppendLine("    articulo_id, descripcion_articulo, cantidad, valor_total, monto_minimo_inicial,")
$null = $builder.AppendLine("    total_abonado, saldo_pendiente, fecha_promesa_entrega, fecha_entrega, observacion,")
$null = $builder.AppendLine("    creado_en, actualizado_en")
$null = $builder.AppendLine(")")
$null = $builder.AppendLine("SELECT")
$null = $builder.AppendLine("    pg_temp.legacy_uuid('legacy-separado', ls.legacy_id::text),")
$null = $builder.AppendLine("    'SEP-LEGACY-' || LPAD(ls.legacy_id::text, 10, '0'),")
$null = $builder.AppendLine("    ls.fecha_separacion,")
$null = $builder.AppendLine("    CASE WHEN ls.restante <= 0 THEN 'PAGADO' ELSE 'ACTIVO' END,")
$null = $builder.AppendLine("    COALESCE(NULLIF(ls.cliente, ''), 'CLIENTE LEGACY'),")
$null = $builder.AppendLine("    NULL,")
$null = $builder.AppendLine("    NULL,")
$null = $builder.AppendLine("    NULL,")
$null = $builder.AppendLine("    COALESCE(NULLIF(ls.articulos, ''), 'Articulo legacy'),")
$null = $builder.AppendLine("    1.000,")
$null = $builder.AppendLine("    ls.costo_total,")
$null = $builder.AppendLine("    LEAST(20000::numeric(19,2), GREATEST(ls.costo_total, 0.00)),")
$null = $builder.AppendLine("    LEAST(ls.costo_total, GREATEST(ls.costo_total - ls.restante, 0.00)),")
$null = $builder.AppendLine("    GREATEST(ls.restante, 0.00),")
$null = $builder.AppendLine("    NULL,")
$null = $builder.AppendLine("    NULL,")
$null = $builder.AppendLine("    CASE")
$null = $builder.AppendLine("        WHEN ls.tiene_descuadre THEN")
$null = $builder.AppendLine("            'Migrado desde SQL Server dbo.Separadoes. Legacy id=' || ls.legacy_id ||")
$null = $builder.AppendLine("            '. Advertencia: total_abonado ajustado desde saldo legacy; suma_abonos_legacy=' || ls.total_abonos")
$null = $builder.AppendLine("        ELSE")
$null = $builder.AppendLine("            'Migrado desde SQL Server dbo.Separadoes. Legacy id=' || ls.legacy_id")
$null = $builder.AppendLine("    END,")
$null = $builder.AppendLine("    ls.fecha_separacion::timestamp,")
$null = $builder.AppendLine("    COALESCE((")
$null = $builder.AppendLine("        SELECT MAX(la.fecha_abono)")
$null = $builder.AppendLine("        FROM legacy_abonos la")
$null = $builder.AppendLine("        WHERE la.legacy_separado_id = ls.legacy_id")
$null = $builder.AppendLine("    ), ls.fecha_separacion::timestamp)")
$null = $builder.AppendLine("FROM legacy_separados ls")
$null = $builder.AppendLine("ON CONFLICT (numero_separado) DO UPDATE SET")
$null = $builder.AppendLine("    fecha_separacion = EXCLUDED.fecha_separacion,")
$null = $builder.AppendLine("    estado = EXCLUDED.estado,")
$null = $builder.AppendLine("    nombre_cliente = EXCLUDED.nombre_cliente,")
$null = $builder.AppendLine("    descripcion_articulo = EXCLUDED.descripcion_articulo,")
$null = $builder.AppendLine("    cantidad = EXCLUDED.cantidad,")
$null = $builder.AppendLine("    valor_total = EXCLUDED.valor_total,")
$null = $builder.AppendLine("    monto_minimo_inicial = EXCLUDED.monto_minimo_inicial,")
$null = $builder.AppendLine("    total_abonado = EXCLUDED.total_abonado,")
$null = $builder.AppendLine("    saldo_pendiente = EXCLUDED.saldo_pendiente,")
$null = $builder.AppendLine("    observacion = EXCLUDED.observacion,")
$null = $builder.AppendLine("    actualizado_en = EXCLUDED.actualizado_en;")
$null = $builder.AppendLine()
$null = $builder.AppendLine("CREATE TEMP TABLE legacy_separado_target AS")
$null = $builder.AppendLine("SELECT")
$null = $builder.AppendLine("    ls.legacy_id,")
$null = $builder.AppendLine("    s.id AS separado_id,")
$null = $builder.AppendLine("    s.numero_separado")
$null = $builder.AppendLine("FROM legacy_separados ls")
$null = $builder.AppendLine("JOIN separados s ON s.numero_separado = 'SEP-LEGACY-' || LPAD(ls.legacy_id::text, 10, '0');")
$null = $builder.AppendLine()

$null = $builder.AppendLine("INSERT INTO ventas (")
$null = $builder.AppendLine("    id, numero_venta, fecha_venta, estado, origen,")
$null = $builder.AppendLine("    monto_manual_informado, subtotal, descuento_total, impuesto_total, total,")
$null = $builder.AppendLine("    monto_recibido, cambio_entregado, observacion, cierre_diario_id, creado_en, actualizado_en")
$null = $builder.AppendLine(")")
$null = $builder.AppendLine("SELECT")
$null = $builder.AppendLine("    pg_temp.legacy_uuid('legacy-abono-venta', la.legacy_id::text),")
$null = $builder.AppendLine("    'VTA-MIG-ABONO-' || LPAD(la.legacy_id::text, 10, '0'),")
$null = $builder.AppendLine("    la.fecha_abono,")
$null = $builder.AppendLine("    'CERRADA',")
$null = $builder.AppendLine("    'SEPARADO',")
$null = $builder.AppendLine("    la.valor_abono,")
$null = $builder.AppendLine("    la.valor_abono,")
$null = $builder.AppendLine("    0,")
$null = $builder.AppendLine("    0,")
$null = $builder.AppendLine("    la.valor_abono,")
$null = $builder.AppendLine("    la.valor_abono,")
$null = $builder.AppendLine("    0,")
$null = $builder.AppendLine("    'Venta sintetica creada por migracion de abono legacy ' || la.legacy_id || ' del separado ' || la.legacy_separado_id,")
$null = $builder.AppendLine("    NULL,")
$null = $builder.AppendLine("    la.fecha_abono,")
$null = $builder.AppendLine("    la.fecha_abono")
$null = $builder.AppendLine("FROM legacy_abonos la")
$null = $builder.AppendLine("JOIN legacy_separados ls ON ls.legacy_id = la.legacy_separado_id")
$null = $builder.AppendLine("ON CONFLICT (numero_venta) DO UPDATE SET")
$null = $builder.AppendLine("    fecha_venta = EXCLUDED.fecha_venta,")
$null = $builder.AppendLine("    estado = EXCLUDED.estado,")
$null = $builder.AppendLine("    origen = EXCLUDED.origen,")
$null = $builder.AppendLine("    monto_manual_informado = EXCLUDED.monto_manual_informado,")
$null = $builder.AppendLine("    subtotal = EXCLUDED.subtotal,")
$null = $builder.AppendLine("    descuento_total = EXCLUDED.descuento_total,")
$null = $builder.AppendLine("    impuesto_total = EXCLUDED.impuesto_total,")
$null = $builder.AppendLine("    total = EXCLUDED.total,")
$null = $builder.AppendLine("    monto_recibido = EXCLUDED.monto_recibido,")
$null = $builder.AppendLine("    cambio_entregado = EXCLUDED.cambio_entregado,")
$null = $builder.AppendLine("    observacion = EXCLUDED.observacion,")
$null = $builder.AppendLine("    cierre_diario_id = EXCLUDED.cierre_diario_id,")
$null = $builder.AppendLine("    actualizado_en = EXCLUDED.actualizado_en;")
$null = $builder.AppendLine()
$null = $builder.AppendLine("CREATE TEMP TABLE legacy_abono_venta_target AS")
$null = $builder.AppendLine("SELECT")
$null = $builder.AppendLine("    la.legacy_id,")
$null = $builder.AppendLine("    la.legacy_separado_id,")
$null = $builder.AppendLine("    v.id AS venta_id")
$null = $builder.AppendLine("FROM legacy_abonos la")
$null = $builder.AppendLine("JOIN ventas v ON v.numero_venta = 'VTA-MIG-ABONO-' || LPAD(la.legacy_id::text, 10, '0');")
$null = $builder.AppendLine()

$null = $builder.AppendLine("INSERT INTO detalle_venta (")
$null = $builder.AppendLine("    id, venta_id, articulo_id, tipo_detalle, orden, descripcion, cantidad, unidad_medida,")
$null = $builder.AppendLine("    precio_unitario, subtotal, descuento, impuesto, total, referencia_externa, creado_en, actualizado_en")
$null = $builder.AppendLine(")")
$null = $builder.AppendLine("SELECT")
$null = $builder.AppendLine("    pg_temp.legacy_uuid('legacy-abono-detalle', la.legacy_id::text),")
$null = $builder.AppendLine("    lavt.venta_id,")
$null = $builder.AppendLine("    NULL,")
$null = $builder.AppendLine("    'MANUAL',")
$null = $builder.AppendLine("    1,")
$null = $builder.AppendLine("    'Abono separado ' || ('SEP-LEGACY-' || LPAD(la.legacy_separado_id::text, 10, '0')) || ' - ' || COALESCE(NULLIF(ls.articulos, ''), 'Articulo legacy'),")
$null = $builder.AppendLine("    1.000,")
$null = $builder.AppendLine("    'UND',")
$null = $builder.AppendLine("    la.valor_abono,")
$null = $builder.AppendLine("    la.valor_abono,")
$null = $builder.AppendLine("    0,")
$null = $builder.AppendLine("    0,")
$null = $builder.AppendLine("    la.valor_abono,")
$null = $builder.AppendLine("    'ABONO-LEGACY-' || la.legacy_id,")
$null = $builder.AppendLine("    la.fecha_abono,")
$null = $builder.AppendLine("    la.fecha_abono")
$null = $builder.AppendLine("FROM legacy_abonos la")
$null = $builder.AppendLine("JOIN legacy_separados ls ON ls.legacy_id = la.legacy_separado_id")
$null = $builder.AppendLine("JOIN legacy_abono_venta_target lavt ON lavt.legacy_id = la.legacy_id")
$null = $builder.AppendLine("ON CONFLICT (id) DO UPDATE SET")
$null = $builder.AppendLine("    venta_id = EXCLUDED.venta_id,")
$null = $builder.AppendLine("    tipo_detalle = EXCLUDED.tipo_detalle,")
$null = $builder.AppendLine("    orden = EXCLUDED.orden,")
$null = $builder.AppendLine("    descripcion = EXCLUDED.descripcion,")
$null = $builder.AppendLine("    cantidad = EXCLUDED.cantidad,")
$null = $builder.AppendLine("    unidad_medida = EXCLUDED.unidad_medida,")
$null = $builder.AppendLine("    precio_unitario = EXCLUDED.precio_unitario,")
$null = $builder.AppendLine("    subtotal = EXCLUDED.subtotal,")
$null = $builder.AppendLine("    descuento = EXCLUDED.descuento,")
$null = $builder.AppendLine("    impuesto = EXCLUDED.impuesto,")
$null = $builder.AppendLine("    total = EXCLUDED.total,")
$null = $builder.AppendLine("    referencia_externa = EXCLUDED.referencia_externa,")
$null = $builder.AppendLine("    actualizado_en = EXCLUDED.actualizado_en;")
$null = $builder.AppendLine()

$null = $builder.AppendLine("INSERT INTO abonos_separado (")
$null = $builder.AppendLine("    id, separado_id, venta_id, numero_abono, fecha_abono, monto_abono, abono_inicial, observacion, creado_en, actualizado_en")
$null = $builder.AppendLine(")")
$null = $builder.AppendLine("SELECT")
$null = $builder.AppendLine("    pg_temp.legacy_uuid('legacy-abono', la.legacy_id::text),")
$null = $builder.AppendLine("    lst.separado_id,")
$null = $builder.AppendLine("    lavt.venta_id,")
$null = $builder.AppendLine("    la.numero_abono,")
$null = $builder.AppendLine("    la.fecha_abono,")
$null = $builder.AppendLine("    la.valor_abono,")
$null = $builder.AppendLine("    (la.numero_abono = 1),")
$null = $builder.AppendLine("    'Migrado desde SQL Server dbo.Abonoes. Legacy id=' || la.legacy_id,")
$null = $builder.AppendLine("    la.fecha_abono,")
$null = $builder.AppendLine("    la.fecha_abono")
$null = $builder.AppendLine("FROM legacy_abonos la")
$null = $builder.AppendLine("JOIN legacy_separado_target lst ON lst.legacy_id = la.legacy_separado_id")
$null = $builder.AppendLine("JOIN legacy_abono_venta_target lavt ON lavt.legacy_id = la.legacy_id")
$null = $builder.AppendLine("ON CONFLICT (venta_id) DO UPDATE SET")
$null = $builder.AppendLine("    separado_id = EXCLUDED.separado_id,")
$null = $builder.AppendLine("    numero_abono = EXCLUDED.numero_abono,")
$null = $builder.AppendLine("    fecha_abono = EXCLUDED.fecha_abono,")
$null = $builder.AppendLine("    monto_abono = EXCLUDED.monto_abono,")
$null = $builder.AppendLine("    abono_inicial = EXCLUDED.abono_inicial,")
$null = $builder.AppendLine("    observacion = EXCLUDED.observacion,")
$null = $builder.AppendLine("    actualizado_en = EXCLUDED.actualizado_en;")
$null = $builder.AppendLine()
$null = $builder.AppendLine("COMMIT;")

$outputDirectory = Split-Path -Parent $OutputPath
if (-not [string]::IsNullOrWhiteSpace($outputDirectory) -and -not (Test-Path $outputDirectory)) {
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
}

[System.IO.File]::WriteAllText($OutputPath, $builder.ToString(), [System.Text.Encoding]::UTF8)

Write-Host "Archivo generado en: $OutputPath"
Write-Host ("Cierres: {0} | Separados: {1} | Abonos: {2}" -f $cierres.Count, $separados.Count, $abonos.Count)
