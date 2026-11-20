package com.posdesktop.pos.facturacion.electronica.delivery;
/** El servidor rechazo la autenticacion antes de aceptar DATA: se puede corregir y crear otra entrega. */
public final class FiscalMailAuthenticationException extends FiscalMailPreflightException {
 public FiscalMailAuthenticationException(){super("El servidor SMTP rechazo las credenciales. Corrige usuario y contrasena antes de una nueva entrega.");}
}
