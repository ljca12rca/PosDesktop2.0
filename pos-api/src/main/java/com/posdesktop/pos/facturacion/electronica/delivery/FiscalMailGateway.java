package com.posdesktop.pos.facturacion.electronica.delivery;
import java.util.UUID;
public interface FiscalMailGateway {
 void send(FiscalDeliverySettingsService.Credentials settings,UUID requestId,String recipient,String number,byte[] bundle);
}
