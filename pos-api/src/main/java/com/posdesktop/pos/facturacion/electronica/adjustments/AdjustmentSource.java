package com.posdesktop.pos.facturacion.electronica.adjustments;

import com.posdesktop.pos.invoicing.domain.FiscalInvoice;
import com.posdesktop.pos.facturacion.electronica.fiscal.FiscalDraftService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Fuente congelada de la factura: nunca obtiene sus importes de una venta editada. */
public record AdjustmentSource(UUID id,UUID saleId,UUID customerId,String provider,String environment,String account,String baseUrl,
        String number,String remoteId,String cufe,LocalDate date,String customer,BigDecimal total,List<FiscalInvoice.Line> lines,
        FiscalDraftService.Snapshot dian,Map<String,Object> nativeInvoice,String hash) {
    public AdjustmentSource {lines=List.copyOf(lines);nativeInvoice=nativeInvoice==null?Map.of():Map.copyOf(nativeInvoice);}
    public String context(){return provider+"|"+account+"|"+environment;}
    public String key(){return context()+"|"+number;}
}
