package com.posdesktop.pos.modelo.relacional;

import com.posdesktop.pos.modelo.comun.EntidadAuditable;
import com.posdesktop.pos.modelo.enumeraciones.MetodoPagoFactura;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "pagos_factura")
public class PagoFactura extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "factura_proveedor_id", nullable = false)
    private FacturaProveedor facturaProveedor;

    @Column(nullable = false)
    private LocalDate fechaPago;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal montoPago = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MetodoPagoFactura metodoPago = MetodoPagoFactura.TRANSFERENCIA;

    @Column(length = 100)
    private String referenciaPago;

    @Column(length = 500)
    private String observacion;

    public UUID getId() {
        return id;
    }

    public FacturaProveedor getFacturaProveedor() {
        return facturaProveedor;
    }

    public void setFacturaProveedor(FacturaProveedor facturaProveedor) {
        this.facturaProveedor = facturaProveedor;
    }

    public LocalDate getFechaPago() {
        return fechaPago;
    }

    public void setFechaPago(LocalDate fechaPago) {
        this.fechaPago = fechaPago;
    }

    public BigDecimal getMontoPago() {
        return montoPago;
    }

    public void setMontoPago(BigDecimal montoPago) {
        this.montoPago = montoPago == null ? BigDecimal.ZERO : montoPago;
    }

    public MetodoPagoFactura getMetodoPago() {
        return metodoPago;
    }

    public void setMetodoPago(MetodoPagoFactura metodoPago) {
        this.metodoPago = metodoPago;
    }

    public String getReferenciaPago() {
        return referenciaPago;
    }

    public void setReferenciaPago(String referenciaPago) {
        this.referenciaPago = referenciaPago;
    }

    public String getObservacion() {
        return observacion;
    }

    public void setObservacion(String observacion) {
        this.observacion = observacion;
    }
}
