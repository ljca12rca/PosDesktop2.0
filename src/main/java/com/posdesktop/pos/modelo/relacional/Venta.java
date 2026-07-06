package com.posdesktop.pos.modelo.relacional;

import com.posdesktop.pos.modelo.comun.EntidadAuditable;
import com.posdesktop.pos.modelo.enumeraciones.EstadoVenta;
import com.posdesktop.pos.modelo.enumeraciones.OrigenVenta;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ventas")
public class Venta extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 40)
    private String numeroVenta;

    @Column(nullable = false)
    private LocalDateTime fechaVenta = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoVenta estado = EstadoVenta.REGISTRADA;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrigenVenta origen = OrigenVenta.MANUAL;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal montoManualInformado = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal descuentoTotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal impuestoTotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(length = 500)
    private String observacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cierre_diario_id")
    private CierreDiario cierreDiario;

    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC")
    private List<DetalleVenta> detalles = new ArrayList<>();

    @OneToOne(mappedBy = "venta")
    private AbonoSeparado abonoSeparado;

    public UUID getId() {
        return id;
    }

    public String getNumeroVenta() {
        return numeroVenta;
    }

    public void setNumeroVenta(String numeroVenta) {
        this.numeroVenta = numeroVenta;
    }

    public LocalDateTime getFechaVenta() {
        return fechaVenta;
    }

    public void setFechaVenta(LocalDateTime fechaVenta) {
        this.fechaVenta = fechaVenta;
    }

    public EstadoVenta getEstado() {
        return estado;
    }

    public void setEstado(EstadoVenta estado) {
        this.estado = estado;
    }

    public OrigenVenta getOrigen() {
        return origen;
    }

    public void setOrigen(OrigenVenta origen) {
        this.origen = origen;
    }

    public BigDecimal getMontoManualInformado() {
        return montoManualInformado;
    }

    public void setMontoManualInformado(BigDecimal montoManualInformado) {
        this.montoManualInformado = valorSeguro(montoManualInformado);
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getDescuentoTotal() {
        return descuentoTotal;
    }

    public BigDecimal getImpuestoTotal() {
        return impuestoTotal;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getObservacion() {
        return observacion;
    }

    public void setObservacion(String observacion) {
        this.observacion = observacion;
    }

    public CierreDiario getCierreDiario() {
        return cierreDiario;
    }

    public void setCierreDiario(CierreDiario cierreDiario) {
        this.cierreDiario = cierreDiario;
    }

    public List<DetalleVenta> getDetalles() {
        return detalles;
    }

    public AbonoSeparado getAbonoSeparado() {
        return abonoSeparado;
    }

    public void agregarDetalle(DetalleVenta detalle) {
        if (detalle == null) {
            return;
        }
        detalle.setVenta(this);
        detalles.add(detalle);
        recalcularTotales();
    }

    public void registrarVentaManual(String descripcion, BigDecimal valorManual) {
        detalles.clear();
        montoManualInformado = valorSeguro(valorManual);
        agregarDetalle(DetalleVenta.crearDetalleManual(1, descripcion, montoManualInformado));
        origen = OrigenVenta.MANUAL;
    }

    public void recalcularTotales() {
        subtotal = detalles.stream()
                .map(DetalleVenta::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        descuentoTotal = detalles.stream()
                .map(DetalleVenta::getDescuento)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        impuestoTotal = detalles.stream()
                .map(DetalleVenta::getImpuesto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        total = detalles.stream()
                .map(DetalleVenta::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal valorSeguro(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }
}
