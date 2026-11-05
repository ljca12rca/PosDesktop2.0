package com.posdesktop.pos.facturacion.electronica.fiscal;
/** Fallo local comprobado antes de iniciar cualquier solicitud remota. */
public class FiscalPreflightException extends IllegalArgumentException {
    public FiscalPreflightException(String message) { super(message); }
}
