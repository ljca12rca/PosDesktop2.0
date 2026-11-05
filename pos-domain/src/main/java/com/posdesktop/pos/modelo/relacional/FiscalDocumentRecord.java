package com.posdesktop.pos.modelo.relacional;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Evidencia de solo insercion, tambien protegida por trigger PostgreSQL. */
@Entity @Table(name = "documentos_fiscales")
public class FiscalDocumentRecord {
    @Id private UUID id;
    @Column(name = "venta_id", nullable = false) private UUID ventaId;
    @Column(name = "cliente_id") private UUID clienteId;
    @Column(nullable = false, length = 20) private String proposito;
    @Column(nullable = false, length = 20) private String proveedor;
    @Column(nullable = false, length = 20) private String ambiente;
    @Column(nullable = false, length = 80) private String cuenta;
    @Column(nullable = false, length = 80) private String numero;
    @Column(nullable = false, length = 40) private String formato;
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "text") private String snapshotJson;
    @Column(columnDefinition = "text") private String xml;
    @Column(nullable = false, unique = true, length = 64) private String huella;
    @Column(name = "cliente_nombre", nullable = false, length = 200) private String clienteNombre;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal total;
    @Column(name = "creado_en", nullable = false) private LocalDateTime creadoEn;
    protected FiscalDocumentRecord() { }
    public FiscalDocumentRecord(UUID ventaId, UUID clienteId, String proposito, String proveedor, String ambiente,
            String cuenta, String numero, String formato, String snapshotJson, String xml, String huella, String clienteNombre, BigDecimal total) {
        this.id = UUID.randomUUID(); this.ventaId = ventaId; this.clienteId = clienteId;
        this.proposito = proposito; this.proveedor = proveedor; this.ambiente = ambiente; this.cuenta = cuenta;
        this.numero = numero; this.formato = formato; this.snapshotJson = snapshotJson; this.xml = xml;
        this.huella = huella; this.clienteNombre = clienteNombre; this.total = total; this.creadoEn = LocalDateTime.now();
    }
    public UUID getId() { return id; }
    public UUID getVentaId() { return ventaId; }
    public UUID getClienteId() { return clienteId; }
    public String getProposito() { return proposito; }
    public String getProveedor() { return proveedor; }
    public String getAmbiente() { return ambiente; }
    public String getCuenta() { return cuenta; }
    public String getNumero() { return numero; }
    public String getFormato() { return formato; }
    public String getSnapshotJson() { return snapshotJson; }
    public String getXml() { return xml; }
    public String getHuella() { return huella; }
    public String getClienteNombre() { return clienteNombre; }
    public BigDecimal getTotal() { return total; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
}
