package com.posdesktop.pos.modelo.relacional;

import com.posdesktop.pos.modelo.comun.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "abonos_separado")
public class AbonoSeparado extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "separado_id", nullable = false)
    private Separado separado;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venta_id", nullable = false, unique = true)
    private Venta venta;

    @Column(nullable = false)
    private Integer numeroAbono = 1;

    @Column(nullable = false)
    private LocalDateTime fechaAbono = LocalDateTime.now();

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal montoAbono = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean abonoInicial = false;

    @Column(length = 500)
    private String observacion;

    public UUID getId() {
        return id;
    }

    public Separado getSeparado() {
        return separado;
    }

    public void setSeparado(Separado separado) {
        this.separado = separado;
    }

    public Venta getVenta() {
        return venta;
    }

    public void setVenta(Venta venta) {
        this.venta = venta;
    }

    public Integer getNumeroAbono() {
        return numeroAbono;
    }

    public void setNumeroAbono(Integer numeroAbono) {
        this.numeroAbono = numeroAbono == null ? 1 : numeroAbono;
    }

    public LocalDateTime getFechaAbono() {
        return fechaAbono;
    }

    public void setFechaAbono(LocalDateTime fechaAbono) {
        this.fechaAbono = fechaAbono;
    }

    public BigDecimal getMontoAbono() {
        return montoAbono;
    }

    public void setMontoAbono(BigDecimal montoAbono) {
        this.montoAbono = montoAbono == null ? BigDecimal.ZERO : montoAbono;
    }

    public boolean isAbonoInicial() {
        return abonoInicial;
    }

    public void setAbonoInicial(boolean abonoInicial) {
        this.abonoInicial = abonoInicial;
    }

    public String getObservacion() {
        return observacion;
    }

    public void setObservacion(String observacion) {
        this.observacion = observacion;
    }
}
