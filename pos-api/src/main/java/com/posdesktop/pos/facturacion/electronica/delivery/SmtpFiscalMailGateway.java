package com.posdesktop.pos.facturacion.electronica.delivery;

import jakarta.mail.*;
import jakarta.mail.internet.*;
import java.util.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.*;
import org.springframework.stereotype.Component;

@Component
public final class SmtpFiscalMailGateway implements FiscalMailGateway {
 @Override public void send(FiscalDeliverySettingsService.Credentials credentials,UUID requestId,String recipient,String number,byte[] bundle){
  var c=credentials.configuration();if(!"SMTP".equals(c.mode()))throw new IllegalArgumentException("El modo no es SMTP.");
  var sender=new JavaMailSenderImpl();sender.setHost(c.host());sender.setPort(c.port());sender.setDefaultEncoding("UTF-8");sender.setProtocol("smtp");
  if(c.username()!=null&&!c.username().isBlank()){sender.setUsername(c.username());sender.setPassword(credentials.password());}
  var p=sender.getJavaMailProperties();p.setProperty("mail.smtp.auth",Boolean.toString(c.username()!=null&&!c.username().isBlank()));p.setProperty("mail.smtp.connectiontimeout","10000");p.setProperty("mail.smtp.timeout","15000");p.setProperty("mail.smtp.writetimeout","15000");p.setProperty("mail.smtp.ssl.checkserveridentity","true");
  if("SSL".equals(c.security()))p.setProperty("mail.smtp.ssl.enable","true");else if("STARTTLS".equals(c.security())){p.setProperty("mail.smtp.starttls.enable","true");p.setProperty("mail.smtp.starttls.required","true");}else if(!"LOCAL".equals(c.security())||!Set.of("127.0.0.1","localhost").contains(c.host()))throw new IllegalArgumentException("SMTP requiere TLS fuera de localhost.");
  try{
   MimeMessage message=new MimeMessage(sender.getSession()){@Override protected void updateMessageID()throws MessagingException{setHeader("Message-ID","<"+requestId+"@posdesktop.local>");}};
   var helper=new MimeMessageHelper(message,true,"UTF-8");helper.setFrom(c.from(),c.senderName());helper.setTo(FiscalDeliverySettingsService.address(recipient));helper.setSubject("[PRUEBAS - SIN VALIDEZ FISCAL] "+number);
   helper.setText("Documento de prueba generado por POSDesktop. Este mensaje no constituye entrega de una factura expedida ni acredita aceptacion por DIAN. El ZIP adjunto incluye XML, PDF y las respuestas disponibles para revision local.",false);
   helper.addAttachment("PRUEBAS-"+FiscalDocumentFiles.safe(number)+".zip",new ByteArrayResource(bundle),"application/zip");message.saveChanges();
   var transport=sender.getSession().getTransport("smtp");try{
    try{transport.connect(c.host(),c.port(),c.username()==null||c.username().isBlank()?null:c.username(),credentials.password());}
    catch(AuthenticationFailedException e){throw new FiscalMailAuthenticationException();}
    catch(MessagingException e){throw new FiscalMailPreflightException("No se pudo conectar o negociar TLS con SMTP antes de enviar el mensaje. Revisa servidor, puerto y seguridad para crear una nueva entrega.");}
    transport.sendMessage(message,message.getAllRecipients());
   }finally{try{transport.close();}catch(MessagingException ignored){}}
  }catch(FiscalMailPreflightException e){throw e;}
  catch(Exception e){throw new IllegalStateException("No se confirmo la entrega SMTP. Consulta el historial antes de repetir el envio.");}
 }
}
