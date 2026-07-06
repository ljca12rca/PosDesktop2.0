package com.posdesktop.pos.modelo.relacional;

import com.posdesktop.pos.modelo.comun.EntidadAuditable;
import com.posdesktop.pos.modelo.enumeraciones.EstadoCierreDiario;
import com.posdesktop.pos.modelo.enumeraciones.EstadoVenta;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "cierres_diarios")
public class CierreDiario extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private LocalDate fechaOperacion;

    @Column(nullable = false)
    private LocalDateTime fechaHoraCierre = LocalDateTime.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoCierreDiario estado = EstadoCierreDiario.ABIERTO;

    @Column(nullable = false)
    private int cantidadVentas = 0;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotalCalculado = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal descuentoCalculado = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal impuestoCalculado = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalCalculado = BigDecimal.ZERO;

    @Column(length = 500)
    private String observacion;

    @OneToMany(mappedBy = "cierreDiario", cascade = CascadeType.PERSIST)
    private List<Venta> ventas = new ArrayList<>();

    public UUID getId() {
        return id;
    }

    public LocalDate getFechaOperacion() {
        return fechaOperacion;
    }

    public void setFechaOperacion(LocalDate fechaOperacion) {
        this.fechaOperacion = fechaOperacion;
    }

    public LocalDateTime getFechaHoraCierre() {
        return fechaHoraCierre;
    }

    public void setFechaHoraCierre(LocalDateTime fechaHoraCierre) {
        this.fechaHoraCierre = fechaHoraCierre;
    }

    public EstadoCierreDiario getEstado() {
        return estado;
    }

    public void setEstado(EstadoCierreDiario estado) {
        this.estado = estado;
    }

    public int getCantidadVentas() {
        return cantidadVentas;
    }

    public BigDecimal getSubtotalCalculado() {
        return subtotalCalculado;
    }

    public BigDecimal getDescuentoCalculado() {
        return descuentoCalculado;
    }

    public BigDecimal getImpuestoCalculado() {
        return impuestoCalculado;
    }

    public BigDecimal getTotalCalculado() {
        return totalCalculado;
    }

    public String getObservacion() {
        return observacion;
    }

    public void setObservacion(String observacion) {
        this.observacion = observacion;
    }

    public List<Venta> getVentas() {
        return ventas;
    }

    public void agregarVenta(Venta venta) {
        if (venta == null || ventas.contains(venta)) {
            return;
        }
        venta.setCierreDiario(this);
        ventas.add(venta);
        recalcularTotales();
    }

    public void recalcularTotales() {
        List<Venta> ventasValidas = ventas.stream()
                .filter(venta -> venta.getEstado() != EstadoVenta.ANULADA)
                .toList();

        cantidadVentas = ventasValidas.size();
        subtotalCalculado = ventasValidas.stream()
                .map(Venta::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        descuentoCalculado = ventasValidas.stream()
                .map(Venta::getDescuentoTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        impuestoCalculado = ventasValidas.stream()
                .map(Venta::getImpuestoTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        totalCalculado = ventasValidas.stream()
                .map(Venta::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
