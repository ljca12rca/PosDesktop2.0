package com.posdesktop.pos.facturacion.electronica.delivery;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.*;

/** Servidor SMTP de prueba, exclusivamente loopback; captura un mensaje sin reenviarlo. */
public final class LocalSmtpFixture implements AutoCloseable {
 private final ServerSocket server;private final ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor();private final Future<String> received;
 public LocalSmtpFixture()throws IOException{server=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"));server.setSoTimeout(15000);received=executor.submit(this::capture);}
 public int port(){return server.getLocalPort();}
 public MimeMessage message()throws Exception{return new MimeMessage(Session.getInstance(new Properties()),new ByteArrayInputStream(received.get(20,TimeUnit.SECONDS).getBytes(StandardCharsets.US_ASCII)));}
 private String capture()throws IOException{try(var socket=server.accept()){socket.setSoTimeout(15000);var reader=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.US_ASCII));var writer=new PrintWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.US_ASCII),true);respond(writer,"220 localhost test");StringBuilder body=new StringBuilder();boolean data=false;for(String line;(line=reader.readLine())!=null;){if(data){if(line.equals(".")){data=false;respond(writer,"250 queued");}else{if(body.length()+line.length()>16_000_000)throw new IOException("Mensaje de prueba demasiado grande.");body.append(line.startsWith("..")?line.substring(1):line).append("\r\n");}}else if(line.startsWith("DATA")){respond(writer,"354 send");data=true;}else if(line.startsWith("QUIT")){respond(writer,"221 bye");break;}else respond(writer,"250 OK");}return body.toString();}}
 private static void respond(PrintWriter writer,String text){writer.print(text+"\r\n");writer.flush();}
 @Override public void close()throws IOException{server.close();executor.shutdownNow();}
}
