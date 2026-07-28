package com.posdesktop.pos.modelo.relacional;

import com.posdesktop.pos.modelo.comun.EntidadAuditable;
import com.posdesktop.pos.modelo.enumeraciones.EstadoFacturaProveedor;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "facturas_proveedor")
public class FacturaProveedor extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proveedor_id", nullable = false)
    private Proveedor proveedor;

    @Column(nullable = false, length = 60)
    private String numeroFactura;

    @Column(nullable = false)
    private LocalDate fechaEmision;

    private LocalDate fechaVencimiento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EstadoFacturaProveedor estado = EstadoFacturaProveedor.REGISTRADA;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal montoTotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal montoPagado = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal saldoPendiente = BigDecimal.ZERO;

    @Column(length = 500)
    private String observacion;

    @OneToMany(mappedBy = "facturaProveedor", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PagoFactura> pagos = new ArrayList<>();

    public UUID getId() {
        return id;
    }

    public Proveedor getProveedor() {
        return proveedor;
    }

    public void setProveedor(Proveedor proveedor) {
        this.proveedor = proveedor;
    }

    public String getNumeroFactura() {
        return numeroFactura;
    }

    public void setNumeroFactura(String numeroFactura) {
        this.numeroFactura = numeroFactura;
    }

    public LocalDate getFechaEmision() {
        return fechaEmision;
    }

    public void setFechaEmision(LocalDate fechaEmision) {
        this.fechaEmision = fechaEmision;
    }

    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }

    public void setFechaVencimiento(LocalDate fechaVencimiento) {
        this.fechaVencimiento = fechaVencimiento;
    }

    public EstadoFacturaProveedor getEstado() {
        return estado;
    }

    public void setEstado(EstadoFacturaProveedor estado) {
        this.estado = estado;
    }

    public BigDecimal getMontoTotal() {
        return montoTotal;
    }

    public void setMontoTotal(BigDecimal montoTotal) {
        this.montoTotal = valorSeguro(montoTotal);
        recalcularSaldo();
    }

    public BigDecimal getMontoPagado() {
        return montoPagado;
    }

    public BigDecimal getSaldoPendiente() {
        return saldoPendiente;
    }

    public String getObservacion() {
        return observacion;
    }

    public void setObservacion(String observacion) {
        this.observacion = observacion;
    }

    public List<PagoFactura> getPagos() {
        return pagos;
    }

    public void registrarPago(PagoFactura pagoFactura) {
        if (pagoFactura == null) {
            return;
        }
        pagoFactura.setFacturaProveedor(this);
        pagos.add(pagoFactura);
        montoPagado = montoPagado.add(valorSeguro(pagoFactura.getMontoPago()));
        recalcularSaldo();
    }

    public void recalcularSaldo() {
        saldoPendiente = montoTotal.subtract(montoPagado).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        if (saldoPendiente.signum() <= 0 && montoTotal.signum() > 0) {
            saldoPendiente = BigDecimal.ZERO;
            estado = EstadoFacturaProveedor.PAGADA;
            return;
        }
        if (fechaVencimiento != null && fechaVencimiento.isBefore(LocalDate.now())) {
            estado = EstadoFacturaProveedor.VENCIDA;
            return;
        }
        if (montoPagado.signum() > 0) {
            estado = EstadoFacturaProveedor.PARCIALMENTE_PAGADA;
        } else {
            estado = EstadoFacturaProveedor.REGISTRADA;
        }
    }

    private BigDecimal valorSeguro(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }
}
