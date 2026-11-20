package com.posdesktop.pos.modelo.relacional;
import jakarta.persistence.*;
@Entity @Table(name="dian_archivos_consecutivos")
public class DianFileSequence {
    @Id @Column(length=100) private String id;
    @Column(name="siguiente",nullable=false) private long next;
    protected DianFileSequence(){}
    public DianFileSequence(String id){this.id=id;next=1;}
    public long reserve(){if(next>0xffffffffL)throw new IllegalArgumentException("Consecutivo anual de archivos agotado.");return next++;}
}
