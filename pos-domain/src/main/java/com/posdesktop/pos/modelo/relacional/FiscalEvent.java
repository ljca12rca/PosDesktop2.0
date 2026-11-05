package com.posdesktop.pos.modelo.relacional;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name = "eventos_fiscales")
public class FiscalEvent {
    @Id private UUID id;
    @Column(name = "documento_id", nullable = false) private UUID documentoId;
    @Column(nullable = false, length = 40) private String tipo;
    @Column(nullable = false, length = 500) private String detalle;
    @Column(name = "creado_en", nullable = false) private LocalDateTime creadoEn;
    protected FiscalEvent() { }
    public FiscalEvent(UUID documentoId, String tipo, String detalle) {
        id = UUID.randomUUID(); this.documentoId = documentoId; this.tipo = tipo; this.detalle = detalle; creadoEn = LocalDateTime.now();
    }
    public String getTipo() { return tipo; }
    public String getDetalle() { return detalle; }
    public LocalDateTime getCreadoEn() { return creadoEn; }
}
