package com.posdesktop.pos.modelo.relacional;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.*;

@Entity @Table(name="dian_envios")
public class DianSubmission {
    @Id private UUID id;
    @Column(name="documento_id",nullable=false) private UUID documentId;
    @Column(name="origen_documento_id",nullable=false) private UUID sourceDocumentId;
    @Column(name="clave_logica",nullable=false,length=240) private String logicalKey;
    @Column(name="modo",nullable=false,length=20) private String mode;
    @Column(name="ambiente",nullable=false,length=20) private String environment="PRUEBAS";
    @Column(name="emisor_nit",nullable=false,length=10) private String issuerNit;
    @Column(name="numero",nullable=false,length=80) private String number;
    @Column(name="tipo_xml",nullable=false,length=20) private String xmlType;
    @Column(name="cufe_cude",nullable=false,length=96) private String documentKey;
    @Column(name="test_set_id",nullable=false) private UUID testSetId;
    @Column(name="archivo_zip",nullable=false,length=80) private String zipName;
    @Column(name="archivo_xml",nullable=false,length=80) private String xmlName;
    @Column(name="contenido_zip",nullable=false,columnDefinition="bytea") private byte[] zipBytes;
    @Column(name="huella_zip",nullable=false,length=64) private String zipHash;
    @Column(name="certificado_huella",nullable=false,length=64) private String certificateHash;
    @Column(name="alcance_firma",nullable=false,length=80) private String signatureScope;
    @Column(name="estado",nullable=false,length=20) private String state="PREPARADO";
    @Column(name="track_id",length=200) private String trackId;
    @Column(name="mensaje",length=2000) private String message;
    @Column(name="respuesta_json",columnDefinition="text") private String response;
    @Column(name="consultas",nullable=false) private int queries;
    @Column(name="propietario") private UUID owner;
    @Column(name="creado_en",nullable=false) private LocalDateTime createdAt;
    @Column(name="actualizado_en",nullable=false) private LocalDateTime updatedAt;
    @Version private long version;
    protected DianSubmission(){}
    public DianSubmission(UUID doc,UUID source,String logical,String mode,String nit,String number,String type,String key,UUID setId,String zipName,String xmlName,byte[] bytes,String hash,String certificate,String scope){
        id=UUID.randomUUID();documentId=doc;sourceDocumentId=source;logicalKey=logical;this.mode=mode;issuerNit=nit;this.number=number;xmlType=type;documentKey=key;testSetId=setId;this.zipName=zipName;this.xmlName=xmlName;zipBytes=bytes.clone();zipHash=hash;certificateHash=certificate;signatureScope=scope;createdAt=updatedAt=LocalDateTime.now();message="Paquete preparado sin enviar.";
    }
    public void claim(UUID owner,boolean query){state="EN_CURSO";this.owner=owner;if(query)queries++;updatedAt=LocalDateTime.now();}
    public void finish(String state,String trackId,String message,String response){this.state=state;if(trackId!=null)this.trackId=trackId;this.message=message;this.response=response;owner=null;updatedAt=LocalDateTime.now();}
    public UUID getId(){return id;}public UUID getDocumentId(){return documentId;}public UUID getSourceDocumentId(){return sourceDocumentId;}public String getLogicalKey(){return logicalKey;}public String getMode(){return mode;}public String getEnvironment(){return environment;}
    public String getIssuerNit(){return issuerNit;}public String getNumber(){return number;}public String getXmlType(){return xmlType;}public String getDocumentKey(){return documentKey;}public UUID getTestSetId(){return testSetId;}
    public String getZipName(){return zipName;}public String getXmlName(){return xmlName;}public byte[] getZipBytes(){return zipBytes.clone();}public String getZipHash(){return zipHash;}public String getCertificateHash(){return certificateHash;}public String getSignatureScope(){return signatureScope;}
    public String getState(){return state;}public String getTrackId(){return trackId;}public String getMessage(){return message;}public String getResponse(){return response;}public int getQueries(){return queries;}public UUID getOwner(){return owner;}public LocalDateTime getCreatedAt(){return createdAt;}public LocalDateTime getUpdatedAt(){return updatedAt;}
}
