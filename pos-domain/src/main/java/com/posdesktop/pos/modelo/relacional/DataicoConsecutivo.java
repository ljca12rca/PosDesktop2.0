package com.posdesktop.pos.modelo.relacional;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Consecutivo reservado para una numeración de factura electrónica de Dataico. */
@Entity
@Table(name = "dataico_consecutivos")
public class DataicoConsecutivo {
    @Id
    @Column(name = "prefijo", length = 40)
    private String prefijo;

    @Column(name = "siguiente_numero", nullable = false)
    private Long siguienteNumero;

    protected DataicoConsecutivo() { }

    public DataicoConsecutivo(String prefijo, Long siguienteNumero) {
        this.prefijo = prefijo;
        this.siguienteNumero = siguienteNumero;
    }

    public String getPrefijo() { return prefijo; }
    public Long getSiguienteNumero() { return siguienteNumero; }
    public void setSiguienteNumero(Long value) { siguienteNumero = value; }
}
