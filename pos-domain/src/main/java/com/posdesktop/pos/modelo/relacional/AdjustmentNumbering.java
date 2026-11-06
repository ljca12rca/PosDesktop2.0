package com.posdesktop.pos.modelo.relacional;
import jakarta.persistence.*;

@Entity @Table(name="numeraciones_ajustes")
public class AdjustmentNumbering {
    @Id @Column(length=240) private String id;
    @Column(name="prefijo_credito",nullable=false,length=20) private String creditPrefix;
    @Column(name="siguiente_credito",nullable=false) private long creditNext;
    @Column(name="prefijo_debito",nullable=false,length=20) private String debitPrefix;
    @Column(name="siguiente_debito",nullable=false) private long debitNext;
    @Version private long version;
    protected AdjustmentNumbering(){}
    public AdjustmentNumbering(String id){this.id=id;}
    public void configure(String cp,long cn,String dp,long dn){creditPrefix=cp;creditNext=cn;debitPrefix=dp;debitNext=dn;}
    public String prefix(boolean credit){return credit?creditPrefix:debitPrefix;}
    public long next(boolean credit){return credit?creditNext:debitNext;}
    public void reserve(boolean credit){if(next(credit)==Long.MAX_VALUE)throw new IllegalArgumentException("Consecutivo agotado.");if(credit)creditNext++;else debitNext++;}
    public long getVersion(){return version;}
}
