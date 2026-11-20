package com.posdesktop.pos.facturacion.electronica.delivery;

import com.posdesktop.pos.facturacion.electronica.config.DataicoTokenCipher;
import com.posdesktop.pos.facturacion.electronica.fiscal.*;
import com.posdesktop.pos.repositorio.relacional.FiscalDeliverySettingsRepository;
import jakarta.mail.internet.InternetAddress;
import java.nio.file.Path;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FiscalDeliverySettingsService {
 private final FiscalDeliverySettingsRepository repository;private final FiscalJson json;private final DataicoTokenCipher cipher;
 public FiscalDeliverySettingsService(FiscalDeliverySettingsRepository repository,FiscalJson json,@Value("${pos.invoicing.delivery-key-file:}")String file){this.repository=repository;this.json=json;String local=System.getenv("LOCALAPPDATA");Path fallback=local==null?Path.of(System.getProperty("user.home"),".posdesktop","secrets","delivery.key"):Path.of(local,"PosDesktop","secrets","delivery.key");cipher=new DataicoTokenCipher(file==null||file.isBlank()?fallback.toString():file);}
 public record Configuration(String mode,String host,int port,String security,String username,String from,String senderName,Set<String> responseCertificateFingerprints){public Configuration{responseCertificateFingerprints=responseCertificateFingerprints==null?Set.of():Set.copyOf(responseCertificateFingerprints);}}
 public record Settings(Configuration configuration,long version,boolean passwordConfigured){}
 public record Save(Configuration configuration,long version,String password){@Override public String toString(){return "DeliverySave[REDACTED]";}}
 public record Credentials(Configuration configuration,long version,String password){@Override public String toString(){return "DeliveryCredentials[REDACTED]";}}
 public static Configuration defaults(){return new Configuration("SIMULACION","",587,"STARTTLS","","","POSDesktop",Set.of());}
 @Transactional(readOnly=true) public Settings get(){var row=repository.findById(1L).orElseThrow();return new Settings(row.getData()==null?defaults():json.read(row.getData(),Configuration.class),row.getVersion(),row.getSecret()!=null);}
 @Transactional public Settings save(Save request){if(request==null||request.configuration()==null)throw new IllegalArgumentException("Falta la configuracion de entrega.");var c=request.configuration();validate(c);var row=repository.lock().orElseThrow();if(row.getVersion()!=request.version())throw new FiscalConflictException("La configuracion cambio. Recarga antes de guardar.");row.setData(json.write(c));if(request.password()!=null&&!request.password().isEmpty()){if(request.password().length()>1000)throw new IllegalArgumentException("Contrasena SMTP demasiado larga.");row.setSecret(cipher.cifrar(request.password()));}repository.saveAndFlush(row);return new Settings(c,row.getVersion(),row.getSecret()!=null);}
 @Transactional(readOnly=true) public Credentials credentials(){var row=repository.findById(1L).orElseThrow();var c=row.getData()==null?defaults():json.read(row.getData(),Configuration.class);return new Credentials(c,row.getVersion(),row.getSecret()==null?null:cipher.descifrar(row.getSecret()));}
 private static void validate(Configuration c){if(!Set.of("SIMULACION","SMTP").contains(Objects.toString(c.mode(),""))||!Set.of("STARTTLS","SSL","LOCAL").contains(Objects.toString(c.security(),""))||c.port()<1||c.port()>65535)throw new IllegalArgumentException("Revisa modo, puerto y seguridad SMTP.");for(String value:List.of(Objects.toString(c.host(),""),Objects.toString(c.username(),""),Objects.toString(c.from(),""),Objects.toString(c.senderName(),"")))if(value.contains("\r")||value.contains("\n")||value.length()>254)throw new IllegalArgumentException("Hay valores de correo invalidos.");if("SMTP".equals(c.mode())){if(c.host()==null||!c.host().matches("[A-Za-z0-9.-]{1,253}"))throw new IllegalArgumentException("Indica el servidor SMTP sin URL ni rutas.");address(c.from());if("LOCAL".equals(c.security())&&!Set.of("127.0.0.1","localhost").contains(c.host()))throw new IllegalArgumentException("Sin TLS solo se admite un servidor de pruebas en localhost.");}if(c.responseCertificateFingerprints().size()>10||c.responseCertificateFingerprints().stream().anyMatch(f->!f.matches("[a-f0-9]{64}")))throw new IllegalArgumentException("Usa huellas SHA-256 de certificados DIAN, en hexadecimal minuscula.");}
 public static String address(String text){try{if(text==null||text.length()>254||text.contains("\r")||text.contains("\n"))throw new IllegalArgumentException();var address=new InternetAddress(text,true);address.validate();if(address.getPersonal()!=null||!address.getAddress().equals(text)||!text.contains("@"))throw new IllegalArgumentException();return text;}catch(Exception e){throw new IllegalArgumentException("Ingresa una sola direccion de correo valida, sin nombres ni listas.");}}
}
