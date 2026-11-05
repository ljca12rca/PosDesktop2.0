package com.posdesktop.pos.invoicing.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Politica local de borrador; no sustituye la validacion de reglas monetarias DIAN. */
public final class Amounts {
    public static final int SCALE = 2;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;
    private Amounts() { }

    public static BigDecimal money(BigDecimal value) {
        return value.setScale(SCALE, ROUNDING);
    }

    public static <T> T required(T value, String field) {
        if (value == null) throw new InvoiceValidationException(field, "REQUIRED", "Campo obligatorio.");
        return value;
    }

    public static BigDecimal nonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) {
            throw new InvoiceValidationException(field, "NON_NEGATIVE", "Se requiere un decimal no negativo.");
        }
        return value;
    }

    public static String text(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvoiceValidationException(field, "REQUIRED", "Campo obligatorio.");
        }
        return value.trim();
    }
}
