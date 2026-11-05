package com.posdesktop.pos.modelo.relacional;

import jakarta.persistence.*;

@Entity @Table(name = "facturacion_configuracion")
public class FiscalProviderSettings {
    @Id private Integer id = 1;
    @Column(nullable = false, length = 20) private String proveedor = "DATAICO";
    @Version private Long version = 0L;
    public String getProveedor() { return proveedor; }
    public void setProveedor(String value) { proveedor = value; }
    public Long getVersion() { return version; }
}
