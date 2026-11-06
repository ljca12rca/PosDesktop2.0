package com.posdesktop.pos.invoicing.domain;

/** Acciones del negocio; los tipos tributarios quedan en los adaptadores. */
public enum AdjustmentKind {
    RETURN("Devolución", "CreditNote", "1", "DEVOLUCION"),
    CANCELLATION("Anulación", "CreditNote", "2", "ANULACION"),
    INCREASE("Ajustar valor", "DebitNote", "3", "CAMBIO_VALOR");
    private final String label, root, reason, dataicoReason;
    AdjustmentKind(String label,String root,String reason,String dataicoReason){this.label=label;this.root=root;this.reason=reason;this.dataicoReason=dataicoReason;}
    public String label(){return label;}
    public String root(){return root;}
    public String reason(){return reason;}
    public String dataicoReason(){return dataicoReason;}
    public boolean credit(){return this!=INCREASE;}
}
