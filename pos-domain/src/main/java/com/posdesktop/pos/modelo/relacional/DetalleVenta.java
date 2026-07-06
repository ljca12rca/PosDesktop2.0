package com.posdesktop.pos.modelo.relacional;

import com.posdesktop.pos.modelo.comun.EntidadAuditable;
import com.posdesktop.pos.modelo.enumeraciones.TipoDetalleVenta;
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
import java.util.UUID;

@Entity
@Table(name = "detalle_venta")
public class DetalleVenta extends EntidadAuditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "articulo_id")
    private Articulo articulo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoDetalleVenta tipoDetalle = TipoDetalleVenta.MANUAL;

    @Column(nullable = false)
    private Integer orden = 1;

    @Column(nullable = false, length = 200)
    private String descripcion;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal cantidad = BigDecimal.ONE;

    @Column(nullable = false, length = 20)
    private String unidadMedida = "UND";

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal precioUnitario = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal descuento = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal impuesto = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal total = BigDecimal.ZERO;

    @Column(length = 100)
    private String referenciaExterna;

    public static DetalleVenta crearDetalleManual(
            int orden,
            String descripcion,
            BigDecimal cantidad,
            BigDecimal precioUnitario
    ) {
        DetalleVenta detalle = new DetalleVenta();
        detalle.setOrden(orden);
        detalle.setTipoDetalle(TipoDetalleVenta.MANUAL);
        detalle.setDescripcion(descripcion == null || descripcion.isBlank() ? "Venta manual" : descripcion);
        detalle.setCantidad(cantidad == null ? BigDecimal.ONE : cantidad);
        detalle.setPrecioUnitario(precioUnitario == null ? BigDecimal.ZERO : precioUnitario);
        detalle.setSubtotal(BigDecimal.ZERO);
        detalle.setDescuento(BigDecimal.ZERO);
        detalle.setImpuesto(BigDecimal.ZERO);
        detalle.setTotal(BigDecimal.ZERO);
        detalle.recalcularTotales();
        return detalle;
    }

    public UUID getId() {
        return id;
    }

    public Venta getVenta() {
        return venta;
    }

    public void setVenta(Venta venta) {
        this.venta = venta;
    }

    public Articulo getArticulo() {
        return articulo;
    }

    public void setArticulo(Articulo articulo) {
        this.articulo = articulo;
    }

    public TipoDetalleVenta getTipoDetalle() {
        return tipoDetalle;
    }

    public void setTipoDetalle(TipoDetalleVenta tipoDetalle) {
        this.tipoDetalle = tipoDetalle;
    }

    public Integer getOrden() {
        return orden;
    }

    public void setOrden(Integer orden) {
        this.orden = orden == null ? 1 : orden;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public BigDecimal getCantidad() {
        return cantidad;
    }

    public void setCantidad(BigDecimal cantidad) {
        this.cantidad = valorSeguro(cantidad, BigDecimal.ONE);
    }

    public String getUnidadMedida() {
        return unidadMedida;
    }

    public void setUnidadMedida(String unidadMedida) {
        this.unidadMedida = unidadMedida;
    }

    public BigDecimal getPrecioUnitario() {
        return precioUnitario;
    }

    public void setPrecioUnitario(BigDecimal precioUnitario) {
        this.precioUnitario = valorSeguro(precioUnitario, BigDecimal.ZERO);
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = valorSeguro(subtotal, BigDecimal.ZERO);
    }

    public BigDecimal getDescuento() {
        return descuento;
    }

    public void setDescuento(BigDecimal descuento) {
        this.descuento = valorSeguro(descuento, BigDecimal.ZERO);
    }

    public BigDecimal getImpuesto() {
        return impuesto;
    }

    public void setImpuesto(BigDecimal impuesto) {
        this.impuesto = valorSeguro(impuesto, BigDecimal.ZERO);
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = valorSeguro(total, BigDecimal.ZERO);
    }

    public String getReferenciaExterna() {
        return referenciaExterna;
    }

    public void setReferenciaExterna(String referenciaExterna) {
        this.referenciaExterna = referenciaExterna;
    }

    public void recalcularTotales() {
        subtotal = cantidad.multiply(precioUnitario);
        total = subtotal.add(impuesto).subtract(descuento);
    }

    private BigDecimal valorSeguro(BigDecimal valor, BigDecimal fallback) {
        return valor == null ? fallback : valor;
    }
}
