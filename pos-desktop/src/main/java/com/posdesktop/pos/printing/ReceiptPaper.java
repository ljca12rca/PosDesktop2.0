package com.posdesktop.pos.printing;

/** Dimensiones compartidas con el comprobante termico del POS (puntos, 72 por pulgada). */
public final class ReceiptPaper {
    public static final double WIDTH_POINTS = 226.8; // 80 mm, mismo valor del POS normal.
    public static final double MIN_HEIGHT_POINTS = 260;

    private ReceiptPaper() { }
}
