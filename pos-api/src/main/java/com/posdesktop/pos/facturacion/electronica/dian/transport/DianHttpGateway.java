package com.posdesktop.pos.facturacion.electronica.dian.transport;

import com.posdesktop.pos.invoicing.infrastructure.signature.DianSoapEnvelope;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import org.springframework.stereotype.Component;

/** Destino oficial fijo. No expone URL configurable ni permite redirecciones. */
@Component
public final class DianHttpGateway implements DianTransportGateway {
    public static final URI HABILITATION=URI.create("https://vpfe-hab.dian.gov.co/WcfDianCustomerServices.svc");
    private final HttpClient http;private final URI endpoint;
    public DianHttpGateway(){this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build(),HABILITATION);}
    DianHttpGateway(HttpClient http,URI endpoint){if(http.followRedirects()!=HttpClient.Redirect.NEVER||(!HABILITATION.equals(endpoint)&&!("http".equals(endpoint.getScheme())&&LOOPBACK.contains(endpoint.getHost()))))throw new IllegalArgumentException("Destino SOAP no permitido.");this.http=http;this.endpoint=endpoint;}
    private static final java.util.Set<String> LOOPBACK=java.util.Set.of("127.0.0.1","localhost","[::1]");
    @Override public String mode(){return "HABILITACION";}
    @Override public Response exchange(DianSoapEnvelope.Request request,Context context){
        try{
            var message=HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(45)).header("Content-Type","application/soap+xml; charset=utf-8; action=\""+request.action()+"\"").header("Accept","application/soap+xml").POST(HttpRequest.BodyPublishers.ofString(request.xml(),StandardCharsets.UTF_8)).build();
            var pending=http.sendAsync(message,info->new LimitedBody());
            try{var result=pending.get(45,TimeUnit.SECONDS);return new Response(result.statusCode(),new String(result.body(),StandardCharsets.UTF_8));}
            finally{if(!pending.isDone())pending.cancel(true);}
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("La solicitud DIAN fue interrumpida; consulta el intento existente.");}
        catch(ExecutionException|TimeoutException e){throw new IllegalStateException("La comunicacion DIAN no confirmo un resultado; consulta el intento existente.");}
    }
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate=HttpResponse.BodySubscribers.ofByteArray();private Flow.Subscription subscription;private long count;
        public CompletionStage<byte[]> getBody(){return delegate.getBody();}
        public void onSubscribe(Flow.Subscription value){subscription=value;delegate.onSubscribe(value);}
        public void onNext(List<ByteBuffer> buffers){for(var buffer:buffers)count+=buffer.remaining();if(count>8_000_000){subscription.cancel();delegate.onError(new IllegalArgumentException("Respuesta SOAP demasiado grande."));}else delegate.onNext(buffers);}
        public void onError(Throwable error){delegate.onError(error);}public void onComplete(){delegate.onComplete();}
    }
}
