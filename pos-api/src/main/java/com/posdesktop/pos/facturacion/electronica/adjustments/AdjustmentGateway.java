package com.posdesktop.pos.facturacion.electronica.adjustments;
import java.util.Map;

/** Puerto de transporte; no contiene reglas comerciales ni persistencia. */
public interface AdjustmentGateway {
    record Result(String id,String number,String dianStatus,String cude,String pdfUrl,String message) { }
    Map<String,Object> invoice(String id);
    Result send(AdjustmentSnapshot snapshot);
    Result consult(AdjustmentSnapshot snapshot);
}
