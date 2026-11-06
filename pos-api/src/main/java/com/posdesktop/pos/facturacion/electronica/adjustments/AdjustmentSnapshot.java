package com.posdesktop.pos.facturacion.electronica.adjustments;
import com.posdesktop.pos.invoicing.domain.AdjustmentCalculator;
import java.time.OffsetDateTime;
import java.util.Map;

public record AdjustmentSnapshot(int schemaVersion,String requestHash,AdjustmentSource source,AdjustmentCalculator.Plan plan,String prefix,long sequence,
        OffsetDateTime issuedAt,String observation,Map<String,Object> providerRequest) {
    public String number(){return prefix+sequence;}
}
