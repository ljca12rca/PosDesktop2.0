package com.posdesktop.pos.modelo.relacional;
import jakarta.persistence.*;
@Entity @Table(name="fiscal_entrega_configuracion")
public class FiscalDeliverySettings {
 @Id private Long id;
 @Column(name="datos_json",columnDefinition="text") private String data;
 @Column(name="secreto_cifrado",columnDefinition="text") private String secret;
 @Version private long version;
 public String getData(){return data;}public void setData(String value){data=value;}
 public String getSecret(){return secret;}public void setSecret(String value){secret=value;}public long getVersion(){return version;}
}
