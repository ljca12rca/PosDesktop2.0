package com.posdesktop.pos.modelo.relacional;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Ajustes operativos y token cifrado; nunca se devuelve esta entidad al cliente. */
@Entity
@Table(name = "dataico_configuracion")
public class DataicoConfiguracion {
    @Id private Integer id = 1;
    @Column(length = 40) private String prefijo;
    @Column(length = 40) private String resolucion;
    @Column(length = 20) private String ambiente;
    private Boolean flexible;
    @Column(name = "token_cifrado", columnDefinition = "text") private String tokenCifrado;
    @Column(name = "account_id", length = 36) private String accountId;
    private Boolean habilitado;
    @Column(name = "base_url", length = 200) private String baseUrl;
    @Column(name = "connect_timeout_seconds") private Integer connectTimeoutSeconds;
    @Column(name = "read_timeout_seconds") private Integer readTimeoutSeconds;
    @Version @Column(nullable = false) private Long version = 0L;

    public Integer getId() { return id; }
    public String getPrefijo() { return prefijo; }
    public void setPrefijo(String value) { prefijo = value; }
    public String getResolucion() { return resolucion; }
    public void setResolucion(String value) { resolucion = value; }
    public String getAmbiente() { return ambiente; }
    public void setAmbiente(String value) { ambiente = value; }
    public Boolean getFlexible() { return flexible; }
    public void setFlexible(Boolean value) { flexible = value; }
    public Long getVersion() { return version; }
    public String getTokenCifrado() { return tokenCifrado; }
    public void setTokenCifrado(String value) { tokenCifrado = value; }
    public String getAccountId() { return accountId; }
    public void setAccountId(String value) { accountId = value; }
    public Boolean getHabilitado() { return habilitado; }
    public void setHabilitado(Boolean value) { habilitado = value; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String value) { baseUrl = value; }
    public Integer getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
    public void setConnectTimeoutSeconds(Integer value) { connectTimeoutSeconds = value; }
    public Integer getReadTimeoutSeconds() { return readTimeoutSeconds; }
    public void setReadTimeoutSeconds(Integer value) { readTimeoutSeconds = value; }
}
