package com.posdesktop.pos.modelo.relacional;

import com.posdesktop.pos.modelo.comun.EntidadAuditable;
import com.posdesktop.pos.modelo.enumeraciones.EstadoSeparado;
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
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "separados")
public class Separado extends EntidadAuditable {

    private static final BigDecimal MONTO_MINIMO_INICIAL_COP = new BigDecimal("20000.00");

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 40)
    private String numeroSeparado;

    @Column(nullable = false)
    private LocalDate fechaSeparacion = LocalDate.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSeparado estado = EstadoSeparado.ACTIVO;

    @Column(nullable = false, length = 160)
    private String nombreCliente;

    @Column(length = 40)
    private String documentoCliente;

    @Column(length = 40)
    private String telefonoCliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "articulo_id")
    private Articulo articulo;

    @Column(nullable = false, length = 200)
    private String descripcionArticulo;

    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal cantidad = BigDecimal.ONE;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal valorTotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal montoMinimoInicial = MONTO_MINIMO_INICIAL_COP;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAbonado = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal saldoPendiente = BigDecimal.ZERO;

    private LocalDate fechaPromesaEntrega;

    private LocalDate fechaEntrega;

    @Column(length = 500)
    private String observacion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "responsable_usuario_id", nullable = false)
    private UsuarioSistema responsableUsuario;

    @OneToMany(mappedBy = "separado", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("numeroAbono ASC")
    private List<AbonoSeparado> abonos = new ArrayList<>();

    public UUID getId() {
        return id;
    }

    public String getNumeroSeparado() {
        return numeroSeparado;
    }

    public void setNumeroSeparado(String numeroSeparado) {
        this.numeroSeparado = numeroSeparado;
    }

    public LocalDate getFechaSeparacion() {
        return fechaSeparacion;
    }

    public void setFechaSeparacion(LocalDate fechaSeparacion) {
        this.fechaSeparacion = fechaSeparacion;
    }

    public EstadoSeparado getEstado() {
        return estado;
    }

    public void setEstado(EstadoSeparado estado) {
        this.estado = estado;
    }

    public String getNombreCliente() {
        return nombreCliente;
    }

    public void setNombreCliente(String nombreCliente) {
        this.nombreCliente = nombreCliente;
    }

    public String getDocumentoCliente() {
        return documentoCliente;
    }

    public void setDocumentoCliente(String documentoCliente) {
        this.documentoCliente = documentoCliente;
    }

    public String getTelefonoCliente() {
        return telefonoCliente;
    }

    public void setTelefonoCliente(String telefonoCliente) {
        this.telefonoCliente = telefonoCliente;
    }

    public Articulo getArticulo() {
        return articulo;
    }

    public void setArticulo(Articulo articulo) {
        this.articulo = articulo;
    }

    public String getDescripcionArticulo() {
        return descripcionArticulo;
    }

    public void setDescripcionArticulo(String descripcionArticulo) {
        this.descripcionArticulo = descripcionArticulo;
    }

    public BigDecimal getCantidad() {
        return cantidad;
    }

    public void setCantidad(BigDecimal cantidad) {
        this.cantidad = valorSeguro(cantidad, BigDecimal.ONE);
    }

    public BigDecimal getValorTotal() {
        return valorTotal;
    }

    public void setValorTotal(BigDecimal valorTotal) {
        this.valorTotal = valorSeguro(valorTotal, BigDecimal.ZERO);
        recalcularSaldo();
    }

    public BigDecimal getMontoMinimoInicial() {
        return montoMinimoInicial;
    }

    public void setMontoMinimoInicial(BigDecimal montoMinimoInicial) {
        this.montoMinimoInicial = valorSeguro(montoMinimoInicial, MONTO_MINIMO_INICIAL_COP);
    }

    public BigDecimal getTotalAbonado() {
        return totalAbonado;
    }

    public BigDecimal getSaldoPendiente() {
        return saldoPendiente;
    }

    public LocalDate getFechaPromesaEntrega() {
        return fechaPromesaEntrega;
    }

    public void setFechaPromesaEntrega(LocalDate fechaPromesaEntrega) {
        this.fechaPromesaEntrega = fechaPromesaEntrega;
    }

    public LocalDate getFechaEntrega() {
        return fechaEntrega;
    }

    public void setFechaEntrega(LocalDate fechaEntrega) {
        this.fechaEntrega = fechaEntrega;
    }

    public String getObservacion() {
        return observacion;
    }

    public void setObservacion(String observacion) {
        this.observacion = observacion;
    }

    public UsuarioSistema getResponsableUsuario() {
        return responsableUsuario;
    }

    public void setResponsableUsuario(UsuarioSistema responsableUsuario) {
        this.responsableUsuario = responsableUsuario;
    }

    public List<AbonoSeparado> getAbonos() {
        return abonos;
    }

    public void agregarAbono(AbonoSeparado abono) {
        if (abono == null) {
            return;
        }
        abono.setSeparado(this);
        abonos.add(abono);
        totalAbonado = totalAbonado.add(valorSeguro(abono.getMontoAbono(), BigDecimal.ZERO));
        recalcularSaldo();
    }

    public void recalcularSaldo() {
        saldoPendiente = valorTotal.subtract(totalAbonado);
        if (saldoPendiente.signum() <= 0 && valorTotal.signum() > 0) {
            saldoPendiente = BigDecimal.ZERO;
            estado = EstadoSeparado.PAGADO;
        }
    }

    private BigDecimal valorSeguro(BigDecimal valor, BigDecimal fallback) {
        return valor == null ? fallback : valor;
    }
}
