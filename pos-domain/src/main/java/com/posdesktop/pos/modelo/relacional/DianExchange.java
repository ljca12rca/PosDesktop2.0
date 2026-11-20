package com.posdesktop.pos.modelo.relacional;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity @Table(name="dian_intercambios")
public class DianExchange {
    @Id private UUID id;
    @Column(name="envio_id",nullable=false) private UUID submissionId;
    @Column(name="accion",nullable=false,length=30) private String action;
    @Column(name="solicitud_xml",nullable=false,columnDefinition="text") private String requestXml;
    @Column(name="respuesta_xml",columnDefinition="text") private String responseXml;
    @Column(name="http_status") private Integer httpStatus;
    @Column(name="diagnostico",length=2000) private String diagnosis;
    @Column(name="creado_en",nullable=false) private LocalDateTime createdAt;
    protected DianExchange(){}
    public DianExchange(UUID submission,String action,String request,String response,Integer status,String diagnosis){id=UUID.randomUUID();submissionId=submission;this.action=action;requestXml=request;responseXml=response;httpStatus=status;this.diagnosis=diagnosis;createdAt=LocalDateTime.now();}
    public UUID getId(){return id;}public String getAction(){return action;}public String getResponseXml(){return responseXml;}public Integer getHttpStatus(){return httpStatus;}public String getDiagnosis(){return diagnosis;}public LocalDateTime getCreatedAt(){return createdAt;}
}
