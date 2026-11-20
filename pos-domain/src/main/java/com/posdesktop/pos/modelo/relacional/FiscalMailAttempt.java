package com.posdesktop.pos.modelo.relacional;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
@Entity @Table(name="fiscal_correos")
public class FiscalMailAttempt {
 @Id private UUID id;
 @Column(name="documento_id",nullable=false) private UUID documentId;
 @Column(name="destinatario",nullable=false,length=254) private String recipient;
 @Column(name="modo",nullable=false,length=20) private String mode;
 @Column(name="configuracion_version",nullable=false) private long configurationVersion;
 @Column(name="estado",nullable=false,length=30) private String state;
 @Column(name="mensaje",nullable=false,length=500) private String message;
 @Column(name="paquete",nullable=false,columnDefinition="bytea") private byte[] bundle;
 @Column(name="paquete_huella",nullable=false,length=64) private String bundleHash;
 @Column(name="creado_en",nullable=false) private LocalDateTime createdAt;
 @Column(name="actualizado_en",nullable=false) private LocalDateTime updatedAt;
 @Version private long version;
 protected FiscalMailAttempt(){}
 public FiscalMailAttempt(UUID id,UUID document,String recipient,String mode,long config,byte[] bundle,String hash){this.id=id;documentId=document;this.recipient=recipient;this.mode=mode;configurationVersion=config;this.bundle=bundle.clone();bundleHash=hash;state="EN_CURSO";message="Entrega en curso. No repetir automaticamente.";createdAt=updatedAt=LocalDateTime.now();}
 public void finish(String state,String message){this.state=state;this.message=message;updatedAt=LocalDateTime.now();}
 public UUID getId(){return id;}public UUID getDocumentId(){return documentId;}public String getRecipient(){return recipient;}public String getMode(){return mode;}public String getState(){return state;}public String getMessage(){return message;}public byte[] getBundle(){return bundle.clone();}public String getBundleHash(){return bundleHash;}public long getConfigurationVersion(){return configurationVersion;}public LocalDateTime getCreatedAt(){return createdAt;}
}
