package com.posdesktop.pos.facturacion.electronica.delivery;
import static org.junit.jupiter.api.Assertions.*;
import jakarta.mail.*;
import jakarta.mail.internet.MimeMessage;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class SmtpFiscalMailGatewayTest {
 @Test void sendsMimeThroughLocalSmtpWithStableMessageIdAndTestAttachment()throws Exception{
  try(var server=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"));var executor=Executors.newVirtualThreadPerTaskExecutor()){
   server.setSoTimeout(10000);var received=executor.submit(()->{try(var socket=server.accept()){socket.setSoTimeout(10000);var reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));var writer=new PrintWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.US_ASCII),true);writer.print("220 localhost test\r\n");writer.flush();StringBuilder data=new StringBuilder();boolean inData=false;for(String line;(line=reader.readLine())!=null;){if(inData){if(line.equals(".")){inData=false;writer.print("250 queued\r\n");writer.flush();}else data.append(line.startsWith("..")?line.substring(1):line).append("\r\n");}else if(line.startsWith("DATA")){writer.print("354 send\r\n");writer.flush();inData=true;}else if(line.startsWith("QUIT")){writer.print("221 bye\r\n");writer.flush();break;}else{writer.print("250 OK\r\n");writer.flush();}}return data.toString();}});
   UUID id=UUID.randomUUID();var c=new FiscalDeliverySettingsService.Configuration("SMTP","127.0.0.1",server.getLocalPort(),"LOCAL","","sender@example.test","POSDesktop PRUEBAS",Set.of());
   new SmtpFiscalMailGateway().send(new FiscalDeliverySettingsService.Credentials(c,0,null),id,"customer@example.test","PRUEBA123",new byte[]{80,75,1,2,3});
   var mime=new MimeMessage(Session.getInstance(new Properties()),new ByteArrayInputStream(received.get(15,TimeUnit.SECONDS).getBytes(StandardCharsets.US_ASCII)));assertEquals("<"+id+"@posdesktop.local>",mime.getMessageID());assertTrue(mime.getSubject().contains("SIN VALIDEZ FISCAL"));assertEquals("customer@example.test",mime.getAllRecipients()[0].toString());var mixed=(Multipart)mime.getContent();boolean found=false;for(int i=0;i<mixed.getCount();i++){var part=mixed.getBodyPart(i);if(part.getFileName()!=null){assertEquals("PRUEBAS-PRUEBA123.zip",part.getFileName());assertArrayEquals(new byte[]{80,75,1,2,3},part.getInputStream().readAllBytes());found=true;}}assertTrue(found);
  }
 }
 @Test void rejectsListsAndHeaderInjection(){for(String invalid:List.of("a@example.test,b@example.test","Nombre <a@example.test>","a@example.test\r\nBcc: b@example.test","sin-correo"))assertThrows(IllegalArgumentException.class,()->FiscalDeliverySettingsService.address(invalid));assertEquals("a@example.test",FiscalDeliverySettingsService.address("a@example.test"));}
}
