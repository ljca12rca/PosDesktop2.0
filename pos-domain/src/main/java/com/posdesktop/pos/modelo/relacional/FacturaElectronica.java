package com.posdesktop.pos.modelo.relacional;

import com.posdesktop.pos.modelo.comun.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "facturas_electronicas")
public class FacturaElectronica extends EntidadAuditable {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "venta_id", nullable = false, unique = true) private UUID ventaId;
    @Column(name = "fecha_emision", nullable = false) private LocalDateTime fechaEmision;
    @Column(name = "numeracion", length = 40) private String numeracion;
    @Column(name = "numero_factura", nullable = false, unique = true, length = 80) private String numeroFactura;
    @Column(name = "cliente_nombre", nullable = false, length = 200) private String clienteNombre;
    @Column(name = "cliente_identificacion", nullable = false, length = 40) private String clienteIdentificacion;
    @Column(name = "cliente_email", length = 120) private String clienteEmail;
    @Column(name = "impuesto_total", nullable = false, precision = 19, scale = 2) private BigDecimal impuestoTotal = BigDecimal.ZERO;
    @Column(name = "retencion_total", nullable = false, precision = 19, scale = 2) private BigDecimal retencionTotal = BigDecimal.ZERO;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal total = BigDecimal.ZERO;
    @Column(name = "estado_dian", nullable = false, length = 40) private String estadoDian;
    @Column(name = "estado_cliente", nullable = false, length = 40) private String estadoCliente;
    @Column(name = "estado_correo", nullable = false, length = 40) private String estadoCorreo;
    @Column(name = "mensaje_dian", length = 1000) private String mensajeDian;
    @Column(name = "mensaje_correo", length = 1000) private String mensajeCorreo;
    @Column(name = "pdf_url", length = 1000) private String pdfUrl;
    @Column(name = "identificador_dataico", length = 120) private String identificadorDataico;

    public UUID getId() { return id; }
    public UUID getVentaId() { return ventaId; }
    public void setVentaId(UUID value) { ventaId = value; }
    public LocalDateTime getFechaEmision() { return fechaEmision; }
    public void setFechaEmision(LocalDateTime value) { fechaEmision = value; }
    public String getNumeracion() { return numeracion; }
    public void setNumeracion(String value) { numeracion = value; }
    public String getNumeroFactura() { return numeroFactura; }
    public void setNumeroFactura(String value) { numeroFactura = value; }
    public String getClienteNombre() { return clienteNombre; }
    public void setClienteNombre(String value) { clienteNombre = value; }
    public String getClienteIdentificacion() { return clienteIdentificacion; }
    public void setClienteIdentificacion(String value) { clienteIdentificacion = value; }
    public String getClienteEmail() { return clienteEmail; }
    public void setClienteEmail(String value) { clienteEmail = value; }
    public BigDecimal getImpuestoTotal() { return impuestoTotal; }
    public void setImpuestoTotal(BigDecimal value) { impuestoTotal = value == null ? BigDecimal.ZERO : value; }
    public BigDecimal getRetencionTotal() { return retencionTotal; }
    public void setRetencionTotal(BigDecimal value) { retencionTotal = value == null ? BigDecimal.ZERO : value; }
    public BigDecimal getTotal() { return total; }
    public void setTotal(BigDecimal value) { total = value == null ? BigDecimal.ZERO : value; }
    public String getEstadoDian() { return estadoDian; }
    public void setEstadoDian(String value) { estadoDian = value; }
    public String getEstadoCliente() { return estadoCliente; }
    public void setEstadoCliente(String value) { estadoCliente = value; }
    public String getEstadoCorreo() { return estadoCorreo; }
    public void setEstadoCorreo(String value) { estadoCorreo = value; }
    public String getMensajeDian() { return mensajeDian; }
    public void setMensajeDian(String value) { mensajeDian = value; }
    public String getMensajeCorreo() { return mensajeCorreo; }
    public void setMensajeCorreo(String value) { mensajeCorreo = value; }
    public String getPdfUrl() { return pdfUrl; }
    public void setPdfUrl(String value) { pdfUrl = value; }
    public String getIdentificadorDataico() { return identificadorDataico; }
    public void setIdentificadorDataico(String value) { identificadorDataico = value; }
}
