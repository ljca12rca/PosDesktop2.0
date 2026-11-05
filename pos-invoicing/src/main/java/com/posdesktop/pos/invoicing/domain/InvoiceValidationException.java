package com.posdesktop.pos.invoicing.domain;

public final class InvoiceValidationException extends IllegalArgumentException {
    private final String field;
    private final String rule;

    public InvoiceValidationException(String field, String rule, String description) {
        super(field + " [" + rule + "]: " + description);
        this.field = field;
        this.rule = rule;
    }

    public String field() { return field; }
    public String rule() { return rule; }
}
