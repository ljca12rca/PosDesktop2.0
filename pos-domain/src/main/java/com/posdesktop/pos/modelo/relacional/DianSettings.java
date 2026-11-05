package com.posdesktop.pos.modelo.relacional;

import jakarta.persistence.*;

@Entity @Table(name = "dian_configuraciones")
public class DianSettings {
    @Id @Column(length = 20) private String ambiente;
    @Column(name = "datos_json", columnDefinition = "text") private String datosJson;
    @Column(name = "secretos_cifrados", columnDefinition = "text") private String secretosCifrados;
    @Version private Long version = 0L;
    public String getAmbiente() { return ambiente; }
    public String getDatosJson() { return datosJson; }
    public void setDatosJson(String value) { datosJson = value; }
    public String getSecretosCifrados() { return secretosCifrados; }
    public void setSecretosCifrados(String value) { secretosCifrados = value; }
    public Long getVersion() { return version; }
}
