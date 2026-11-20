package com.posdesktop.pos.facturacion.electronica.delivery;
/** Fallo anterior a SMTP DATA: el servidor no pudo aceptar el mensaje. */
public class FiscalMailPreflightException extends RuntimeException {
 public FiscalMailPreflightException(String message){super(message);}
}
