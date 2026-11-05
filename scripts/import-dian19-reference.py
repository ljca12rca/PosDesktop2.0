"""Extract official DIAN resources without altering the source workbooks or XMLs.

Development only. Inputs must be the PDF and ZIP linked by DIAN's technical portal.
No network, no database access, no invoice submission. Requires bundled openpyxl.
"""
from pathlib import Path
import hashlib
import json
import re
import zipfile
from openpyxl import load_workbook
from io import BytesIO

ROOT = Path(__file__).resolve().parents[1]
REFERENCE = ROOT / "pos-invoicing/target/dian-reference"
DEST = ROOT / "pos-invoicing/src/main/resources/dian19"
TEST = ROOT / "pos-invoicing/src/test/resources/dian19/official"


def code(value, width=0):
    if isinstance(value, (int, float)):
        if int(value) != value:
            return None
        value = str(int(value))
    elif isinstance(value, str):
        value = value.strip()
    else:
        return None
    if not value or not re.fullmatch(r"[A-Z0-9-]+", value):
        return None
    return value.zfill(width) if value.isdigit() else value


def main():
    DEST.mkdir(parents=True, exist_ok=True)
    TEST.mkdir(parents=True, exist_ok=True)
    entries = {}
    provenance = []
    specs = {
        "environment": ("13.1.1.", [(1, 2)], 0),
        "document": ("13.1.3 ", [(1, 2)], 2),
        "operation": ("13.1.5.1 Tipos", [(1, 2)], 2),
        "identification": ("13.2.1 ", [(1, 2)], 2),
        "tax": ("13.2.2 ", [(1, 2)], 2),
        "organization": ("13.2.3.", [(1, 2)], 0),
        "responsibility": ("13.2.6.1 ", [(1, 2)], 0),
        "party-tax": ("13.2.6.2.", [(1, 2)], 0),
        "payment-form": ("13.3.4.1.", [(1, 2)], 0),
        "payment-means": ("13.3.4.2 ", [(1, 2), (3, 4)], 0),
        "unit": ("13.3.6 ", [(1, 2), (3, 4), (5, 6)], 0),
        "adjustment": ("13.3.8.", [(1, 2)], 2),
        "country": ("13.4.1 ", [(3, 1)], 0),
        "department": ("13.4.2 ", [(1, 2), (4, 5)], 2),
        "municipality": ("13.4.3 ", [(3, 5), (7, 9)], 5),
    }
    with zipfile.ZipFile(REFERENCE / "herramientas-2026.zip") as archive:
        for table, (prefix, columns, width) in specs.items():
            path = next(n for n in archive.namelist() if "/Tablas Referenciadas/" in n
                        and n.rsplit("/", 1)[-1].startswith(prefix) and n.endswith(".xlsx"))
            raw = archive.read(path)
            provenance.append({"table": table, "source": path, "sha256": hashlib.sha256(raw).hexdigest()})
            sheet = load_workbook(BytesIO(raw), read_only=True, data_only=True).active
            rows = list(sheet.values)
            for row in rows[2:]:
                for c, label in columns:
                    key = code(row[c], width)
                    name = row[label]
                    if key and isinstance(name, str) and name.strip():
                        # Header words must not become valid codes.
                        if key in {"CODIGO", "IVA", "INC"} and table != "tax":
                            continue
                        entries[(table, key)] = " ".join(name.split())
        rates = next(n for n in archive.namelist() if n.rsplit("/", 1)[-1].startswith("13.3.11 ") and n.endswith(".xlsx"))
        raw = archive.read(rates)
        provenance.append({"table": "rates", "source": rates, "sha256": hashlib.sha256(raw).hexdigest()})
        rows = list(load_workbook(BytesIO(raw), read_only=True, data_only=True).active.values)
        from decimal import Decimal
        for year, column in [(2023, 3), (2024, 4), (2025, 5), (2026, 6)]:
            for row in rows[17:20]:
                value = format(Decimal(str(row[column]).replace("$", "").strip()).normalize(), "f")
                entries[(f"ibua-{year}", value)] = "Tarifa nominal COP por 100 ml; clasificacion explicita del producto requerida"
            raw_rate = rows[21][column]
            rate = Decimal(str(raw_rate).replace("%", ""))
            if not isinstance(raw_rate, str):
                rate *= 100
            entries[(f"icui-{year}", format(rate.normalize(), "f"))] = "Tarifa porcentual ICUI publicada"
        for table, start, end in [("vat-rate", 5, 9), ("inc-rate", 11, 15), ("rete-vat-rate", 22, 24), ("rete-income-rate", 25, 66)]:
            for row in rows[start:end]:
                try:
                    from decimal import Decimal
                    value = format(Decimal(str(row[5])).normalize(), "f")
                    if not value:
                        value = "0"
                    entries[(table, value)] = "Tarifa porcentual publicada; aplicabilidad no inferida"
                except Exception:
                    continue
        for name in archive.namelist():
            if "/XSD/" in name and name.endswith(".xsd"):
                relative = name.split("/XSD/", 1)[1]
                target = DEST / "xsd" / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(archive.read(name))
        # El XSD publicado confunde schemeID (DV) con tipo de documento. El anexo
        # FAC y todos los ejemplos usan DV en schemeID y 31 en schemeName.
        # Conservar original intacto y producir una variante local explicita.
        original = DEST / "xsd/maindoc/DIAN_UBL_Structures.xsd"
        source = original.read_text(encoding="utf-8")
        pattern = r'(<attribute name="schemeID" use="required">.*?<restriction base="xsd:string">).*?(</restriction>)'
        matches = re.findall(pattern, source, flags=re.S)
        if len(matches) != 1:
            raise ValueError("El XSD cambio: revisar la correccion de DV antes de importar")
        corrected = re.sub(pattern, lambda m: m[1] + '\n' + ''.join(f'<enumeration value="{d}"/>' for d in range(10)) + '\n' + m[2], source, flags=re.S)
        (DEST / "xsd/maindoc/DIAN_UBL_Structures-profile19.xsd").write_text(corrected, encoding="utf-8")
        provenance.append({"local_correction": "DIAN_UBL_Structures-profile19.xsd", "field": "coID2Type/@schemeID",
                           "reason": "DV 0..9 segun anexo FAC y ejemplos; original preservado",
                           "original_sha256": hashlib.sha256(original.read_bytes()).hexdigest(),
                           "corrected_sha256": hashlib.sha256(corrected.encode("utf-8")).hexdigest()})
        examples = {
            "Generica.xml": "generic.xml",
            "Exento de IVA.xml": "exempt.xml",
            "Excluido de IVA.xml": "excluded.xml",
            "ExcluidosExentos.xml": "mixed.xml",
            "ejemplificacionIBUA.xml": "ibua.xml",
            "ejemplificacionICUI.xml": "icui.xml",
            "EmisorAutoretenedor.xml": "withholding.xml",
        }
        for original, target in examples.items():
            name = next(n for n in archive.namelist() if "/XMLs de ejemplo/" in n and n.rsplit("/", 1)[-1] == original)
            raw = archive.read(name)
            (TEST / target).write_bytes(raw)
            provenance.append({"example": target, "source": name, "sha256": hashlib.sha256(raw).hexdigest()})
    (DEST / "catalogs.tsv").write_text("".join(f"{table}\t{key}\t{name}\n" for (table, key), name in sorted(entries.items())), encoding="utf-8")
    manifest = {
        "retrieved": "2026-10-30",
        "zip_url": "https://www.dian.gov.co/impuestos/factura-electronica/Documents/Caja-de-herramientas-FE_V19_v2026.zip",
        "pdf_url": "https://www.dian.gov.co/impuestos/factura-electronica/Documents/Anexo-Tecnico-Factura-Electronica-de-Venta-vr-1-9.pdf",
        "zip_sha256": hashlib.sha256((REFERENCE / "herramientas-2026.zip").read_bytes()).hexdigest(),
        "pdf_sha256": hashlib.sha256((REFERENCE / "anexo-fe-1.9.pdf").read_bytes()).hexdigest(),
        "sources": provenance,
    }
    (DEST / "provenance.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    print("Catalog entries:", len(entries), "Official XML fixtures:", len(examples))


if __name__ == "__main__":
    main()
