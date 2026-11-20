package com.posdesktop.pos.facturacion.electronica.delivery;

import com.posdesktop.pos.facturacion.electronica.dian.transport.DianTransportTransactions;
import com.posdesktop.pos.facturacion.electronica.fiscal.FiscalConflictException;
import com.posdesktop.pos.modelo.relacional.*;
import com.posdesktop.pos.repositorio.relacional.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FiscalMailTransactions {
 private final FiscalDeliverySettingsRepository settings;private final FiscalMailRepository mail;private final FiscalEventRepository events;
 public FiscalMailTransactions(FiscalDeliverySettingsRepository settings,FiscalMailRepository mail,FiscalEventRepository events){this.settings=settings;this.mail=mail;this.events=events;}
 public record Claim(FiscalMailAttempt attempt,boolean fresh){}
 @Transactional public Claim claim(UUID id,UUID document,String recipient,String mode,long configuration,byte[] bundle){
  if(id==null)throw new IllegalArgumentException("Falta el identificador de la solicitud de entrega.");var row=settings.lock().orElseThrow();if(row.getVersion()!=configuration)throw new FiscalConflictException("La configuracion de correo cambio. Recarga antes de enviar.");
  var existing=mail.findById(id);if(existing.isPresent()){var attempt=existing.get();if(!attempt.getDocumentId().equals(document)||!attempt.getRecipient().equals(recipient))throw new FiscalConflictException("El identificador ya pertenece a otra entrega.");return new Claim(attempt,false);}
  if(mail.existsByDocumentIdAndRecipientAndStateIn(document,recipient,Set.of("EN_CURSO","INCIERTO")))throw new FiscalConflictException("Ya hay una entrega sin confirmar para este destinatario. Revisa el historial y el servidor SMTP antes de repetir.");
  var attempt=mail.saveAndFlush(new FiscalMailAttempt(id,document,recipient,mode,configuration,bundle,DianTransportTransactions.hash(bundle)));events.save(new FiscalEvent(document,"CORREO_PRUEBA_INICIO","Solicitud de entrega "+id+" en modo "+mode+"."));return new Claim(attempt,true);
 }
 @Transactional public FiscalMailAttempt finish(UUID id,String state,String message){var a=mail.lock(id).orElseThrow();if(!"EN_CURSO".equals(a.getState()))throw new FiscalConflictException("La entrega ya fue registrada.");a.finish(state,message);events.save(new FiscalEvent(a.getDocumentId(),"CORREO_PRUEBA_"+state,message));return mail.saveAndFlush(a);}
 @Transactional(readOnly=true) public List<FiscalMailAttempt> list(UUID id){return mail.findByDocumentIdOrderByCreatedAtDesc(id);}
 @Transactional(readOnly=true) public FiscalMailAttempt get(UUID id){return mail.findById(id).orElseThrow(()->new IllegalArgumentException("No existe la entrega solicitada."));}
}
