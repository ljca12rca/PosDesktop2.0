package com.posdesktop.pos.modelo.relacional;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "facturacion_operaciones")
public class FiscalOperation {
    @Id @Column(name = "venta_id") private UUID ventaId;
    @Column(name = "cliente_id", nullable = false) private UUID clienteId;
    @Column(nullable = false, length = 20) private String proveedor;
    @Column(name = "documento_id", nullable = false) private UUID documentoId;
    @Column(nullable = false, length = 30) private String estado;
    @Column(nullable = false) private UUID propietario;
    @Column(nullable = false) private int intentos;
    @Column(name = "resultado_json", columnDefinition = "text") private String resultadoJson;
    @Version private Long version = 0L;
    @Column(name = "actualizado_en", nullable = false) private LocalDateTime actualizadoEn;
    protected FiscalOperation() { }
    public FiscalOperation(UUID ventaId, UUID clienteId, String proveedor, UUID documentoId, UUID owner) {
        this.ventaId = ventaId; this.clienteId = clienteId; this.proveedor = proveedor;
        this.documentoId = documentoId; this.propietario = owner; intentos = 1; changeState("EN_CURSO");
    }
    public void retry(UUID owner) { propietario = owner; intentos++; changeState("EN_CURSO"); }
    public void changeDocument(UUID documentId) { documentoId = documentId; actualizadoEn = LocalDateTime.now(); }
    public void changeState(String state) { estado = state; actualizadoEn = LocalDateTime.now(); }
    public void complete(String resultJson) { resultadoJson = resultJson; changeState("CONFIRMADO"); }
    public UUID getVentaId() { return ventaId; }
    public UUID getClienteId() { return clienteId; }
    public String getProveedor() { return proveedor; }
    public UUID getDocumentoId() { return documentoId; }
    public UUID getPropietario() { return propietario; }
    public String getEstado() { return estado; }
    public String getResultadoJson() { return resultadoJson; }
    public int getIntentos() { return intentos; }
    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
}
