package com.posdesktop.pos.facturacion.electronica.adjustments;

import com.posdesktop.pos.facturacion.electronica.config.*;
import com.posdesktop.pos.facturacion.electronica.fiscal.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DataicoAdjustmentGateway implements AdjustmentGateway {
    private final Supplier<DataicoProperties> configuration;
    private final Function<DataicoProperties,RestClient> clients;
    @Autowired public DataicoAdjustmentGateway(DataicoConfiguracionService settings){
        this(settings::paraProveedor,p->{var f=new SimpleClientHttpRequestFactory();f.setConnectTimeout(p.connectTimeout());f.setReadTimeout(p.readTimeout());return RestClient.builder().baseUrl(DataicoConfiguracionService.validarUrl(p.baseUrl())).requestFactory(f).build();});
    }
    public DataicoAdjustmentGateway(Supplier<DataicoProperties> configuration,Function<DataicoProperties,RestClient> clients){this.configuration=configuration;this.clients=clients;}
    @Override public Map<String,Object> invoice(String id){var p=ready();return unwrap(clients.apply(p).get().uri("/invoices/{id}",id).headers(h->headers(h,p)).retrieve().body(Map.class),"invoice");}
    @Override public Result send(AdjustmentSnapshot s){var p=matching(s);return result(clients.apply(p).post().uri(path(s)).headers(h->headers(h,p)).contentType(MediaType.APPLICATION_JSON).body(s.providerRequest()).retrieve().body(Map.class),s);}
    @Override public Result consult(AdjustmentSnapshot s){var p=matching(s);return result(clients.apply(p).get().uri(b->b.path(path(s)).queryParam("number",s.number()).build()).headers(h->headers(h,p)).retrieve().body(Map.class),s);}
    private String path(AdjustmentSnapshot s){return s.plan().kind().credit()?"/credit_notes":"/debit_notes";}
    private DataicoProperties matching(AdjustmentSnapshot s){var p=ready();if(!Objects.equals(p.accountId(),s.source().account())||!Objects.equals(p.baseUrl(),s.source().baseUrl()))throw new FiscalPreflightException("La cuenta o URL de Dataico cambio. Restaura la configuracion de esta factura para continuar.");return p;}
    private DataicoProperties ready(){var p=configuration.get();if(!p.enabled()||p.tokenParaProveedor()==null||p.tokenParaProveedor().isBlank())throw new FiscalPreflightException("Habilita Dataico y configura su token.");DataicoConfiguracionService.validarUrl(p.baseUrl());return p;}
    private static void headers(HttpHeaders h,DataicoProperties p){h.set("Auth-token",p.tokenParaProveedor());h.set("Dataico_account_id",p.accountId());h.set("x-on-behalf-of",p.accountId());}
    public static Map<String,Object> request(AdjustmentSource source,com.posdesktop.pos.invoicing.domain.AdjustmentCalculator.Plan plan,String prefix,long sequence,java.time.OffsetDateTime at,String observation){
        Map<String,Object> note=new LinkedHashMap<>();note.put("dataico_account_id",source.account());note.put("env",source.environment());note.put("invoice_id",source.remoteId());
        note.put("issue_date",at.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")));note.put("number",Long.toString(sequence));note.put("numbering",Map.of("prefix",prefix,"flexible",true));
        note.put("reason",plan.kind().dataicoReason());note.put("notes",List.of(observation));if(source.nativeInvoice().containsKey("customer"))note.put("customer",source.nativeInvoice().get("customer"));
        List<?> original=(List<?>)source.nativeInvoice().get("items");
        var items=plan.allocations().stream().map(a->{@SuppressWarnings("unchecked") var item=new LinkedHashMap<>((Map<String,Object>)original.get(a.order()-1));
            item.remove("id");item.remove("uuid");item.remove("total");item.remove("subtotal");item.put("sku",a.line().reference());item.put("description",a.line().description());item.put("quantity",a.line().quantity());item.put("price",a.line().unitPrice());
            if(!plan.kind().credit())item.remove("discount_rate");return item;}).toList();note.put("items",items);
        return Map.of("actions",Map.of("send_dian",true,"send_email",false),plan.kind().credit()?"credit_note":"debit_note",note);
    }
    private Result result(Map<String,Object> response,AdjustmentSnapshot s){var body=unwrap(response,s.plan().kind().credit()?"credit_note":"debit_note");
        String id=text(body,"uuid"),number=text(body,"number");Object numbering=body.get("numbering");String prefix=numbering instanceof Map<?,?> n?Objects.toString(n.get("prefix"),""):"";
        if(id==null||number==null||!(number.equals(s.number())||number.equals(Long.toString(s.sequence()))&&(prefix.isEmpty()||prefix.equals(s.prefix()))))throw new IllegalStateException("Respuesta sin identificador o numeracion verificable. Consulta el intento antes de reenviar.");
        return new Result(id,s.number(),text(body,"dian_status"),text(body,body.get("cude")==null?"cufe":"cude"),text(body,"pdf_url"),text(body,"message"));}
    @SuppressWarnings("unchecked") public static Map<String,Object> unwrap(Map<?,?> body,String key){
        if(body==null)throw new IllegalArgumentException("Dataico no devolvio el documento.");Object value=body.getOrDefault(key,null);
        if(value instanceof Map<?,?> m)return (Map<String,Object>)m;
        value=body.get("data");if(value instanceof Map<?,?> m)return unwrap(m,key);if(value instanceof List<?> list&&list.size()==1&&list.getFirst() instanceof Map<?,?> m)return unwrap(m,key);
        return (Map<String,Object>)body;
    }
    private static String text(Map<String,Object> body,String field){Object v=body.get(field);return v==null?null:v.toString();}
}
