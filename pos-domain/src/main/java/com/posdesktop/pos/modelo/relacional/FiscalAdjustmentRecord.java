package com.posdesktop.pos.modelo.relacional;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name="ajustes_fiscales")
public class FiscalAdjustmentRecord {
    @Id private UUID id;
    @Column(name="solicitud_id",nullable=false,unique=true) private UUID requestId;
    @Column(name="venta_id",nullable=false) private UUID saleId;
    @Column(name="documento_id",nullable=false) private UUID documentId;
    @Column(name="origen_clave",nullable=false,length=240) private String sourceKey;
    @Column(name="origen_hash",nullable=false,length=64) private String sourceHash;
    @Column(name="origen_numero",nullable=false,length=80) private String sourceNumber;
    @Column(name="proveedor",nullable=false,length=20) private String provider;
    @Column(name="ambiente",nullable=false,length=20) private String environment;
    @Column(name="tipo",nullable=false,length=20) private String kind;
    @Column(name="numero",nullable=false,length=80) private String number;
    @Column(nullable=false,precision=19,scale=2) private BigDecimal total;
    @Column(name="estado",nullable=false,length=30) private String state;
    @Column(name="snapshot_json",nullable=false,columnDefinition="text") private String snapshot;
    @Column(name="respuesta_json",columnDefinition="text") private String response;
    @Column(name="propietario") private UUID owner;
    @Column(name="creado_en",nullable=false) private LocalDateTime createdAt;
    @Column(name="actualizado_en",nullable=false) private LocalDateTime updatedAt;
    @Version private long version;
    protected FiscalAdjustmentRecord(){}
    public FiscalAdjustmentRecord(UUID requestId,UUID saleId,UUID documentId,String key,String hash,String sourceNumber,String provider,String environment,String kind,String number,BigDecimal total,String snapshot){
        id=UUID.randomUUID();this.requestId=requestId;this.saleId=saleId;this.documentId=documentId;sourceKey=key;sourceHash=hash;this.sourceNumber=sourceNumber;this.provider=provider;this.environment=environment;this.kind=kind;this.number=number;this.total=total;this.snapshot=snapshot;state="BORRADOR";createdAt=updatedAt=LocalDateTime.now();
    }
    public void transition(String state,UUID owner,String response){this.state=state;this.owner=owner;this.response=response;updatedAt=LocalDateTime.now();}
    public UUID getId(){return id;}public UUID getRequestId(){return requestId;}public UUID getSaleId(){return saleId;}public UUID getDocumentId(){return documentId;}
    public String getSourceKey(){return sourceKey;}public String getSourceHash(){return sourceHash;}public String getSourceNumber(){return sourceNumber;}
    public String getProvider(){return provider;}public String getEnvironment(){return environment;}public String getKind(){return kind;}public String getNumber(){return number;}
    public BigDecimal getTotal(){return total;}public String getState(){return state;}public String getSnapshot(){return snapshot;}public String getResponse(){return response;}public UUID getOwner(){return owner;}
    public LocalDateTime getCreatedAt(){return createdAt;}public LocalDateTime getUpdatedAt(){return updatedAt;}
}
