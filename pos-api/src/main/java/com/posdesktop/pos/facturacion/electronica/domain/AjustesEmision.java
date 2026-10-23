package com.posdesktop.pos.facturacion.electronica.domain;

/** Copia inmutable de los ajustes usados para esta emisión, sin credenciales. */
public record AjustesEmision(String resolucion, String ambiente, boolean flexible) { }
