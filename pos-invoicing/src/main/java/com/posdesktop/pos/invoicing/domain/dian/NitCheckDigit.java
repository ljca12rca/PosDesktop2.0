package com.posdesktop.pos.invoicing.domain.dian;

import com.posdesktop.pos.invoicing.domain.InvoiceValidationException;

public final class NitCheckDigit {
    private static final int[] WEIGHTS = {3,7,13,17,19,23,29,37,41,43,47,53,59,67,71};
    private NitCheckDigit() { }
    public static String calculate(String nit) {
        if (nit == null || !nit.matches("[0-9]{1,15}"))
            throw new InvoiceValidationException("nit", "NIT_FORMAT", "NIT de 1 a 15 digitos, sin separadores ni DV.");
        int sum = 0;
        for (int i = 0; i < nit.length(); i++) sum += (nit.charAt(nit.length() - 1 - i) - '0') * WEIGHTS[i];
        int remainder = sum % 11;
        return Integer.toString(remainder < 2 ? remainder : 11 - remainder);
    }
}
