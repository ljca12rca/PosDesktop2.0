package com.posdesktop.pos.facturacion.electronica.dian.transport;

import com.posdesktop.pos.invoicing.infrastructure.signature.DianSoapEnvelope;
import java.util.UUID;

/** Puerto de intercambio SOAP: el caso de uso conserva estados, evidencia y reglas de reenvio. */
public interface DianTransportGateway {
    String mode();
    record Context(UUID submissionId,String method,String fileName,String xmlName,String documentKey,String zipHash,int queries){ }
    record Response(int httpStatus,String body){ }
    Response exchange(DianSoapEnvelope.Request request,Context context);
}
