package com.posdesktop.pos.modelo.relacional;

import com.posdesktop.pos.modelo.comun.EntidadAuditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

@Entity
@Table(name = "clientes_electronicos", uniqueConstraints = @UniqueConstraint(
        name = "uk_clientes_electronicos_tipo_identificacion",
        columnNames = {"tipo_identificacion", "identificacion"}
))
public class ClienteElectronico extends EntidadAuditable {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "tipo_identificacion", nullable = false, length = 20) private String tipoIdentificacion;
    @Column(name = "tipo_persona", nullable = false, length = 10) private String tipoPersona;
    @Column(nullable = false, length = 40) private String identificacion;
    @Column(name = "digito_verificacion", length = 1) private String digitoVerificacion;
    @Column(name = "razon_social", nullable = false, length = 160) private String razonSocial;
    @Column(name = "primer_nombre", length = 80) private String primerNombre;
    @Column(name = "segundo_nombre", length = 80) private String segundoNombre;
    @Column(name = "primer_apellido", length = 80) private String primerApellido;
    @Column(name = "segundo_apellido", length = 80) private String segundoApellido;
    @Column(length = 120) private String email;
    @Column(length = 40) private String telefono;
    @Column(length = 250) private String direccion;
    @Column(name = "ciudad_codigo_dane", length = 10) private String ciudadCodigoDane;
    @Column(name = "departamento_codigo_dane", length = 10) private String departamentoCodigoDane;
    @Column(name = "pais_codigo", nullable = false, length = 2) private String paisCodigo = "CO";
    public UUID getId() { return id; }
    public String getTipoIdentificacion() { return tipoIdentificacion; }
    public void setTipoIdentificacion(String value) { tipoIdentificacion = value; }
    public String getTipoPersona() { return tipoPersona; }
    public void setTipoPersona(String value) { tipoPersona = value; }
    public String getIdentificacion() { return identificacion; }
    public void setIdentificacion(String value) { identificacion = value; }
    public String getDigitoVerificacion() { return digitoVerificacion; }
    public void setDigitoVerificacion(String value) { digitoVerificacion = value; }
    public String getRazonSocial() { return razonSocial; }
    public void setRazonSocial(String value) { razonSocial = value; }
    public String getPrimerNombre() { return primerNombre; }
    public void setPrimerNombre(String value) { primerNombre = value; }
    public String getSegundoNombre() { return segundoNombre; }
    public void setSegundoNombre(String value) { segundoNombre = value; }
    public String getPrimerApellido() { return primerApellido; }
    public void setPrimerApellido(String value) { primerApellido = value; }
    public String getSegundoApellido() { return segundoApellido; }
    public void setSegundoApellido(String value) { segundoApellido = value; }
    public String getEmail() { return email; }
    public void setEmail(String value) { email = value; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String value) { telefono = value; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String value) { direccion = value; }
    public String getCiudadCodigoDane() { return ciudadCodigoDane; }
    public void setCiudadCodigoDane(String value) { ciudadCodigoDane = value; }
    public String getDepartamentoCodigoDane() { return departamentoCodigoDane; }
    public void setDepartamentoCodigoDane(String value) { departamentoCodigoDane = value; }
    public String getPaisCodigo() { return paisCodigo; }
    public void setPaisCodigo(String value) { paisCodigo = value; }
}
