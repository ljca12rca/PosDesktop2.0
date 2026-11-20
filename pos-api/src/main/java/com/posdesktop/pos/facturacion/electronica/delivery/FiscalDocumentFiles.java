package com.posdesktop.pos.facturacion.electronica.delivery;

import com.posdesktop.pos.facturacion.electronica.fiscal.*;
import com.posdesktop.pos.invoicing.infrastructure.signature.ApplicationResponseVerifier;
import com.posdesktop.pos.invoicing.infrastructure.transport.*;
import com.posdesktop.pos.modelo.relacional.FiscalDocumentRecord;
import com.posdesktop.pos.repositorio.relacional.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.zip.*;
import org.springframework.stereotype.Service;

@Service
public class FiscalDocumentFiles {
 private final FiscalDocumentRepository documents;private final DianSubmissionRepository submissions;private final FiscalDeliverySettingsService settings;private final FiscalJson json;private final FiscalPdfRenderer pdf;
 private final FiscalContainerService containers;
 public FiscalDocumentFiles(FiscalDocumentRepository documents,DianSubmissionRepository submissions,FiscalDeliverySettingsService settings,FiscalJson json,FiscalPdfRenderer pdf,FiscalContainerService containers){this.documents=documents;this.submissions=submissions;this.settings=settings;this.json=json;this.pdf=pdf;this.containers=containers;}
 public FiscalDocumentRecord document(UUID id){var d=documents.findById(id).orElseThrow(()->new IllegalArgumentException("No existe el documento fiscal."));if(!"DIAN_DIRECT".equals(d.getProveedor())||d.getXml()==null||!json.hash(d.getSnapshotJson()+"\n"+d.getXml()).equals(d.getHuella()))throw new IllegalArgumentException("Selecciona una evidencia XML DIAN integra.");return d;}
 public byte[] pdf(UUID id,FiscalPdfRenderer.Paper paper){return pdf.render(FiscalPrintDocument.read(document(id).getXml()),paper);}
 public record Verification(UUID submissionId,String mode,ApplicationResponseVerifier.Result signature){}
 public List<Verification> verifyResponses(UUID id){var d=document(id);var identity=DianDocumentIdentity.read(d.getXml());var trusted=settings.get().configuration().responseCertificateFingerprints();return submissions.findByDocumentIdOrderByCreatedAtDesc(id).stream().filter(s->s.getResponse()!=null).map(s->{var reply=json.read(s.getResponse(),DianSoapResponseParser.Reply.class);return new Verification(s.getId(),s.getMode(),new ApplicationResponseVerifier().verify(reply.applicationResponse(),identity.number(),identity.key(),trusted,Instant.now()));}).toList();}
 public byte[] bundle(UUID id,FiscalPdfRenderer.Paper paper){var d=document(id);String base="PRUEBAS-"+safe(d.getNumero());try(var bytes=new ByteArrayOutputStream()){
  try(var zip=new ZipOutputStream(bytes,StandardCharsets.UTF_8)){entry(zip,base+".xml",d.getXml().getBytes(StandardCharsets.UTF_8));entry(zip,base+".pdf",pdf(id,paper));entry(zip,"LEEME.txt","PAQUETE DE PRUEBAS. SIN VALIDEZ FISCAL. No acredita expedicion, aceptacion DIAN ni entrega oficial al adquiriente. Incluye XML y representacion grafica del snapshot.\n".getBytes(StandardCharsets.UTF_8));
   boolean containerAdded=false;var identity=DianDocumentIdentity.read(d.getXml());
   for(var s:submissions.findByDocumentIdOrderByCreatedAtDesc(id))if(s.getResponse()!=null){var reply=json.read(s.getResponse(),DianSoapResponseParser.Reply.class);if(reply.applicationResponse()!=null){entry(zip,s.getId()+"-ApplicationResponse.xml",reply.applicationResponse().getBytes(StandardCharsets.UTF_8));var checked=new ApplicationResponseVerifier().verify(reply.applicationResponse(),identity.number(),identity.key(),Set.of(),Instant.now());if(!containerAdded&&checked.signatureValid()&&checked.correlated()&&"02".equals(checked.responseCode())){entry(zip,base+"-AttachedDocument.xml",containers.create(d.getXml(),reply.applicationResponse()).getBytes(StandardCharsets.UTF_8));containerAdded=true;}}}
   if(!containerAdded)entry(zip,"CONTENEDOR-PENDIENTE.txt","Sin ApplicationResponse positivo, firmado y correlacionado no se fabrica un AttachedDocument. El paquete conserva los documentos disponibles para prueba local.\n".getBytes(StandardCharsets.UTF_8));
   entry(zip,"verificacion-respuestas.json",json.write(verifyResponses(id)).getBytes(StandardCharsets.UTF_8));
  }return bytes.toByteArray();
 }catch(IOException e){throw new IllegalStateException("No fue posible crear el paquete de entrega.",e);}}
 public static String safe(String value){return value.replaceAll("[^A-Za-z0-9_-]","_");}
 private static void entry(ZipOutputStream zip,String name,byte[] bytes)throws IOException{var entry=new ZipEntry(name);entry.setTimeLocal(java.time.LocalDateTime.of(1980,1,1,0,0));zip.putNextEntry(entry);zip.write(bytes);zip.closeEntry();}
}
