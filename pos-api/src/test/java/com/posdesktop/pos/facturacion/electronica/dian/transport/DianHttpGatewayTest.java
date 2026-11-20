package com.posdesktop.pos.facturacion.electronica.dian.transport;
import static org.junit.jupiter.api.Assertions.*;
import com.posdesktop.pos.invoicing.infrastructure.signature.DianSoapEnvelope;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;

class DianHttpGatewayTest {
    @Test void postsSoap12WithActionAndPreservesErrorBodyWithoutFollowingRedirects()throws Exception{
        var calls=new AtomicInteger();var status=new AtomicInteger(500);var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/soap",e->{calls.incrementAndGet();assertEquals("POST",e.getRequestMethod());assertEquals("<soap/>",new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));assertEquals("application/soap+xml; charset=utf-8; action=\"http://wcf.dian.colombia/IWcfDianCustomerServices/GetStatus\"",e.getRequestHeaders().getFirst("Content-Type"));assertNull(e.getRequestHeaders().getFirst("Authorization"));e.getResponseHeaders().set("Location","/unexpected");byte[] body="<Fault>diagnostico</Fault>".getBytes(StandardCharsets.UTF_8);try(e){e.sendResponseHeaders(status.get(),body.length);e.getResponseBody().write(body);}});
        server.start();try{var gateway=new DianHttpGateway(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/soap"));var request=new DianSoapEnvelope.Request(DianSoapEnvelope.action("GetStatus"),"<soap/>");var response=gateway.exchange(request,null);assertEquals(500,response.httpStatus());assertTrue(response.body().contains("diagnostico"));status.set(307);assertEquals(307,gateway.exchange(request,null).httpStatus());assertEquals(2,calls.get());assertThrows(IllegalArgumentException.class,()->new DianHttpGateway(HttpClient.newHttpClient(),URI.create("https://example.invalid")));}finally{server.stop(0);}
    }
}
