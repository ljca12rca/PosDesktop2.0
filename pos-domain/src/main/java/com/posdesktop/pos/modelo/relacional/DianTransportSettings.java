package com.posdesktop.pos.modelo.relacional;
import jakarta.persistence.*;
@Entity @Table(name="dian_transporte_configuracion")
public class DianTransportSettings {
    @Id private Long id;
    @Column(name="modo",nullable=false,length=20) private String mode;
    @Version private long version;
    protected DianTransportSettings(){}
    public String getMode(){return mode;}public void setMode(String mode){this.mode=mode;}public long getVersion(){return version;}
}
