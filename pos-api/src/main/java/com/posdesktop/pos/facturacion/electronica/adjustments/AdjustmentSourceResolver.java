package com.posdesktop.pos.facturacion.electronica.adjustments;

import com.posdesktop.pos.facturacion.electronica.config.DataicoConfiguracionService;
import com.posdesktop.pos.facturacion.electronica.fiscal.*;
import com.posdesktop.pos.invoicing.domain.*;
import com.posdesktop.pos.invoicing.infrastructure.xml.*;
import com.posdesktop.pos.repositorio.relacional.*;
import com.posdesktop.pos.shared.exception.ResourceNotFoundException;
import java.math.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;

/** Resuelve el proveedor de la factura original. No toma importes de entidades comerciales vivas. */
@Service
public class AdjustmentSourceResolver {
    private final FacturaElectronicaRepositorio invoices;private final FiscalDocumentRepository documents;
    private final DataicoConfiguracionService settings;private final AdjustmentGateway dataico;private final FiscalJson json;
    public record Candidate(UUID id,String provider,String number,String customer,String environment,BigDecimal total) { }
    public AdjustmentSourceResolver(FacturaElectronicaRepositorio invoices,FiscalDocumentRepository documents,DataicoConfiguracionService settings,AdjustmentGateway dataico,FiscalJson json){this.invoices=invoices;this.documents=documents;this.settings=settings;this.dataico=dataico;this.json=json;}
    public List<Candidate> search(String query){
        if(query==null||query.isBlank()||query.length()>80)throw new IllegalArgumentException("Escribe numero de factura o nombre del cliente (hasta 80 caracteres).");
        var result=new ArrayList<Candidate>();
        for(var f:invoices.findTop20ByNumeroFacturaContainingIgnoreCaseOrClienteNombreContainingIgnoreCaseOrderByFechaEmisionDesc(query.trim(),query.trim()))if("DATAICO".equals(f.getProveedor()))result.add(new Candidate(f.getId(),"DATAICO",f.getNumeroFactura(),f.getClienteNombre(),"Consultar",f.getTotal()));
        for(var f:documents.findTop20ByFormatoAndNumeroContainingIgnoreCaseOrderByCreadoEnDesc("DIAN_INVOICE_V1",query.trim()))if("PRUEBAS".equals(f.getAmbiente()))result.add(new Candidate(f.getId(),"DIAN_DIRECT",f.getNumero(),f.getClienteNombre(),f.getAmbiente(),f.getTotal()));
        return result;
    }
    public AdjustmentSource resolve(String provider,UUID id){return switch(provider){case "DIAN_DIRECT"->direct(id);case "DATAICO"->dataico(id);default->throw new IllegalArgumentException("Proveedor de factura invalido.");};}
    private AdjustmentSource direct(UUID id){
        var doc=documents.findById(id).orElseThrow(()->new ResourceNotFoundException("No existe la factura de origen."));
        if(!"DIAN_DIRECT".equals(doc.getProveedor())||!"DIAN_INVOICE_V1".equals(doc.getFormato())||!"PRUEBAS".equals(doc.getAmbiente()))throw new IllegalArgumentException("Selecciona un borrador de factura DIAN del ambiente PRUEBAS.");
        if(!json.hash(doc.getSnapshotJson()+"\n"+doc.getXml()).equals(doc.getHuella()))throw new IllegalArgumentException("La evidencia original no coincide con su huella.");
        var snapshot=json.read(doc.getSnapshotJson(),FiscalDraftService.Snapshot.class);var invoice=snapshot.invoice();
        if(!invoice.adjustments().isEmpty()||!invoice.withholdings().isEmpty()||invoice.payableRounding().signum()!=0)throw new IllegalArgumentException("Esta factura incluye ajustes globales o retenciones. Se requiere distribuirlos explicitamente antes de devolverla.");
        try {
            var xml=DianInvoiceDraftGenerator.parse(doc.getXml());String cufe=xml.getElementsByTagNameNS(DianInvoiceDraftGenerator.CBC,"UUID").item(0).getTextContent();
            new DianAdjustmentGenerator.Reference(invoice.number(),cufe,invoice.issuedAt().toLocalDate());
            return new AdjustmentSource(id,doc.getVentaId(),doc.getClienteId(),"DIAN_DIRECT",doc.getAmbiente(),doc.getCuenta(),null,invoice.number(),null,cufe,invoice.issuedAt().toLocalDate(),doc.getClienteNombre(),doc.getTotal(),invoice.lines(),snapshot,Map.of(),doc.getHuella());
        }catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalArgumentException("No se pudo leer el XML original.");}
    }
    @SuppressWarnings("unchecked") private AdjustmentSource dataico(UUID id){
        var f=invoices.findById(id).orElseThrow(()->new ResourceNotFoundException("No existe la factura de origen."));
        if(!"DATAICO".equals(f.getProveedor())||f.getIdentificadorDataico()==null)throw new IllegalArgumentException("La factura no tiene un identificador Dataico.");
        var properties=settings.paraProveedor();var body=dataico.invoice(f.getIdentificadorDataico());
        String remoteId=Objects.toString(body.get("uuid"),"");if(!f.getIdentificadorDataico().equals(remoteId))throw new IllegalArgumentException("Dataico devolvio otra factura. Revisa la cuenta configurada.");
        String status=Objects.toString(body.get("dian_status"),"");
        if(!Set.of("DIAN_ACEPTADO","ACEPTADO").contains(status.toUpperCase(Locale.ROOT)))throw new IllegalArgumentException("La factura original debe estar aceptada por DIAN antes de ajustarla.");
        String environment=Objects.toString(body.get("env"),"");FiscalSettingsService.environment(environment);
        String prefix=body.get("numbering") instanceof Map<?,?> n?Objects.toString(n.get("prefix"),""):"";
        String number=Objects.toString(body.get("number"),"");if(!f.getNumeroFactura().equals(number)&&!f.getNumeroFactura().equals(prefix+number))throw new IllegalArgumentException("La numeracion original no coincide con Dataico.");
        String account=Objects.toString(body.get("dataico_account_id"),properties.accountId());if(!Objects.equals(account,properties.accountId()))throw new IllegalArgumentException("La factura pertenece a otra cuenta Dataico.");
        if(!(body.get("items") instanceof List<?> items)||items.isEmpty())throw new IllegalArgumentException("Dataico no devolvio los articulos originales.");
        var lines=new ArrayList<FiscalInvoice.Line>();var nativeItems=new ArrayList<Map<String,Object>>();
        for(Object entry:items){
            if(!(entry instanceof Map<?,?> m))throw new IllegalArgumentException("Detalle Dataico invalido.");
            var item=(Map<String,Object>)m;var q=decimal(item.get("quantity"));var price=decimal(item.get("price"));
            BigDecimal discount=item.get("discount_rate")==null?BigDecimal.ZERO:Amounts.money(q.multiply(price).multiply(decimal(item.get("discount_rate"))).movePointLeft(2));
            var taxes=new ArrayList<FiscalInvoice.Tax>();
            if(item.get("taxes") instanceof List<?> ts)for(Object tax:ts){
                if(!(tax instanceof Map<?,?> t))throw new IllegalArgumentException("Tributo Dataico invalido.");
                String category=Objects.toString(t.get("tax_category"),"");String code=switch(category){case "IVA"->"01";case "IMP_CONSUMO"->"04";case "ICA"->"03";case "ICUI"->"35";default->throw new IllegalArgumentException("El tributo "+category+" requiere un adaptador especifico antes de ajustar esta factura.");};
                var rate=decimal(t.get("tax_rate"));taxes.add(new FiscalInvoice.Tax(rate.signum()==0?FiscalInvoice.Treatment.EXEMPT:FiscalInvoice.Treatment.TAXED,code,category,rate));
            }
            // Dataico permite items sin taxes. Esta marca interna NO se exporta como clasificacion DIAN.
            if(taxes.isEmpty())taxes.add(new FiscalInvoice.Tax(FiscalInvoice.Treatment.EXCLUDED,"01","Sin tributos en Dataico",BigDecimal.ZERO));
            lines.add(new FiscalInvoice.Line(Objects.toString(item.get("sku"),Objects.toString(item.get("description"),"Articulo")),Objects.toString(item.get("description"),"Articulo"),q,Objects.toString(item.get("measuring_unit"),"94"),price,discount,BigDecimal.ZERO,taxes));
            var clean=new LinkedHashMap<String,Object>();for(String key:List.of("sku","description","quantity","price","discount_rate","taxes","measuring_unit"))if(item.get(key)!=null)clean.put(key,item.get(key));nativeItems.add(clean);
        }
        BigDecimal computed=lines.stream().map(l->{var a=new InvoiceCalculator().calculateLine(l);return a.net().add(a.taxes().stream().map(InvoiceCalculator.TaxAmount::amount).reduce(BigDecimal.ZERO,BigDecimal::add));}).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(computed.compareTo(f.getTotal())!=0)throw new IllegalArgumentException("El detalle de Dataico no coincide con el total local. Sincroniza la factura antes de ajustar; no se inferiran cargos o impuestos.");
        var nativeInvoice=new LinkedHashMap<String,Object>();nativeInvoice.put("items",nativeItems);if(body.get("customer")!=null)nativeInvoice.put("customer",body.get("customer"));
        String hash=json.hash(json.write(List.of(f.getNumeroFactura(),remoteId,environment,account,computed,nativeInvoice)));
        return new AdjustmentSource(id,f.getVentaId(),null,"DATAICO",environment,account,properties.baseUrl(),f.getNumeroFactura(),remoteId,Objects.toString(body.get("cufe"),null),f.getFechaEmision().toLocalDate(),f.getClienteNombre(),computed,lines,null,nativeInvoice,hash);
    }
    private static BigDecimal decimal(Object value){try{return new BigDecimal(Objects.requireNonNull(value).toString());}catch(Exception e){throw new IllegalArgumentException("Dataico devolvio una cantidad o importe no numerico.");}}
}
