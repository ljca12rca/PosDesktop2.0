package com.posdesktop.pos.facturacion.electronica.delivery;
import com.posdesktop.pos.facturacion.electronica.fiscal.*;
import com.posdesktop.pos.invoicing.infrastructure.signature.*;
import com.posdesktop.pos.invoicing.infrastructure.xml.*;
import com.posdesktop.pos.repositorio.relacional.DianSettingsRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class FiscalContainerService {
 private final DianSettingsRepository settings;private final FiscalSecretVault vault;
 public FiscalContainerService(DianSettingsRepository settings,FiscalSecretVault vault){this.settings=settings;this.vault=vault;}
 public String create(String document,String response){var secrets=vault.decrypt(settings.findById("PRUEBAS").orElseThrow().getSecretosCifrados(),"PRUEBAS");if(secrets.certificateBase64()==null||secrets.certificatePassword()==null)throw new IllegalArgumentException("Carga el certificado local para firmar el contenedor de prueba.");byte[] bytes=Base64.getDecoder().decode(secrets.certificateBase64());char[] password=secrets.certificatePassword().toCharArray();try{var now=Instant.now();var key=Pkcs12SigningKey.load(bytes,password,now);var signed=new DianXadesSignature().sign(new DianAttachedDocumentGenerator().generate(document,response,UUID.randomUUID(),now),key,now);var validation=OfflineUblValidator.dian19Container().validate(signed);if(!validation.structurallyValid())throw new IllegalArgumentException("El contenedor no supero la validacion XSD local.");return signed;}finally{Arrays.fill(bytes,(byte)0);Arrays.fill(password,'\0');}}
}
