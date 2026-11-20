package com.posdesktop.pos.facturacion.electronica.delivery;
import com.posdesktop.pos.modelo.relacional.FiscalMailAttempt;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class FiscalMailService {
 private final FiscalDocumentFiles files;private final FiscalDeliverySettingsService settings;private final FiscalMailTransactions transactions;private final FiscalMailGateway gateway;
 public FiscalMailService(FiscalDocumentFiles files,FiscalDeliverySettingsService settings,FiscalMailTransactions transactions,FiscalMailGateway gateway){this.files=files;this.settings=settings;this.transactions=transactions;this.gateway=gateway;}
 public record Request(UUID requestId,String recipient,FiscalPdfRenderer.Paper paper){}
 public FiscalMailAttempt send(UUID document,Request request){if(request==null||request.paper()==null)throw new IllegalArgumentException("Selecciona el formato de PDF.");String recipient=FiscalDeliverySettingsService.address(request.recipient());var d=files.document(document);var c=settings.credentials();if("SMTP".equals(c.configuration().mode())&&c.configuration().username()!=null&&!c.configuration().username().isBlank()&&c.password()==null)throw new IllegalArgumentException("Configura la contrasena del usuario SMTP antes de enviar.");byte[] bundle=files.bundle(document,request.paper());var claim=transactions.claim(request.requestId(),document,recipient,c.configuration().mode(),c.version(),bundle);if(!claim.fresh())return claim.attempt();
  if("SIMULACION".equals(c.configuration().mode()))return transactions.finish(request.requestId(),"SIMULADO","Entrega simulada localmente. No se envio ningun correo.");
  try{gateway.send(c,request.requestId(),recipient,d.getNumero(),bundle);return transactions.finish(request.requestId(),"ENVIADO","El servidor SMTP acepto el correo de prueba. No confirma lectura ni entrega en la bandeja del destinatario.");}
  catch(FiscalMailPreflightException error){return transactions.finish(request.requestId(),"RECHAZADO",error.getMessage());}
  catch(RuntimeException error){return transactions.finish(request.requestId(),"INCIERTO","No se pudo confirmar el correo. Verifica el servidor SMTP; no se repetira automaticamente.");}
 }
}
