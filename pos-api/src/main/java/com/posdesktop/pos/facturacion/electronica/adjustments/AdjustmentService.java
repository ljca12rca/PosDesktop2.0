package com.posdesktop.pos.facturacion.electronica.adjustments;

import com.posdesktop.pos.facturacion.electronica.fiscal.*;
import com.posdesktop.pos.invoicing.domain.AdjustmentCalculator;
import com.posdesktop.pos.modelo.relacional.FiscalAdjustmentRecord;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

@Service
public class AdjustmentService {
    private final AdjustmentSourceResolver sources;private final AdjustmentTransactions transactions;private final AdjustmentGateway gateway;private final FiscalJson json;
    public AdjustmentService(AdjustmentSourceResolver sources,AdjustmentTransactions transactions,AdjustmentGateway gateway,FiscalJson json){this.sources=sources;this.transactions=transactions;this.gateway=gateway;this.json=json;}
    public record Line(int order,String description,BigDecimal originalQuantity,BigDecimal available,BigDecimal unitPrice,String taxes) { }
    public record Source(UUID id,String provider,String environment,String number,String customer,String sourceHash,BigDecimal total,List<Line> lines,AdjustmentTransactions.Numbering numbering) { }
    public record Summary(UUID id,UUID documentId,String sourceNumber,String number,String provider,String environment,String kind,String state,
            BigDecimal total,LocalDateTime createdAt,String dianStatus,String pdfUrl,String message) { }
    public Source source(String provider,UUID id){var s=sources.resolve(provider,id);var reserved=transactions.reservations(s);var calc=new AdjustmentCalculator();var lines=new ArrayList<Line>();
        for(int i=0;i<s.lines().size();i++){var l=s.lines().get(i);lines.add(new Line(i+1,l.description(),l.quantity(),calc.available(i+1,l,reserved),l.unitPrice(),String.join(" · ",l.taxes().stream().map(t->t.treatment()==com.posdesktop.pos.invoicing.domain.FiscalInvoice.Treatment.EXCLUDED?"Sin tributos":t.schemeName()+" "+t.percent()+"%").toList())));}
        return new Source(s.id(),s.provider(),s.environment(),s.number(),s.customer(),s.hash(),s.total(),lines,transactions.numbering(s));}
    public AdjustmentTransactions.Quote quote(AdjustmentTransactions.Request r){return transactions.quote(sources.resolve(r.provider(),r.sourceId()),r);}
    public Summary create(AdjustmentTransactions.Request r){return summary(transactions.create(sources.resolve(r.provider(),r.sourceId()),r));}
    public Summary send(UUID id,boolean consult){
        var claim=transactions.claim(id,consult);
        try{return summary(transactions.complete(claim,consult?gateway.consult(claim.snapshot()):gateway.send(claim.snapshot())));}
        catch(RuntimeException error){
            boolean certain=!consult&&(error instanceof FiscalPreflightException||error instanceof RestClientResponseException http&&Set.of(400,401,403,404,422).contains(http.getStatusCode().value()));
            String message=certain?"Dataico rechazo la solicitud. Revisa credenciales, numeracion y datos antes de reintentar.":"No se confirmo el resultado. Usa Consultar intento; no generes otro envio.";
            return summary(transactions.failure(claim,certain,message));
        }
    }
    public List<Summary> history(){return transactions.history().stream().map(this::summary).toList();}
    public Summary discard(UUID id){return summary(transactions.discard(id));}
    public AdjustmentTransactions.Numbering numbering(String p,UUID id){return transactions.numbering(sources.resolve(p,id));}
    public AdjustmentTransactions.Numbering configure(String p,UUID id,AdjustmentTransactions.Configure r){return transactions.configure(sources.resolve(p,id),r);}
    private Summary summary(FiscalAdjustmentRecord a){var response=a.getResponse()==null?null:json.read(a.getResponse(),AdjustmentGateway.Result.class);
        return new Summary(a.getId(),a.getDocumentId(),a.getSourceNumber(),a.getNumber(),a.getProvider(),a.getEnvironment(),a.getKind(),a.getState(),a.getTotal(),a.getCreatedAt(),response==null?null:response.dianStatus(),response==null?null:response.pdfUrl(),response==null?null:response.message());}
}
