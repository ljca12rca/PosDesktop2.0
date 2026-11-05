package com.posdesktop.pos.invoicing.domain.dian;

import java.util.Objects;
import java.util.UUID;

/** Naturaleza y forma de pago explicitas; nunca inferidas del documento o de la fecha. */
public record DianProfile(Environment environment, UUID softwareId, String providerNit, String providerDv,
        String issuerOrganization, String customerOrganization, String paymentForm) {
    public DianProfile {
        Objects.requireNonNull(environment); Objects.requireNonNull(softwareId);
        if (!NitCheckDigit.calculate(providerNit).equals(providerDv)) throw new IllegalArgumentException("DV del proveedor invalido.");
        if (!java.util.Set.of("1", "2").contains(issuerOrganization)
                || !java.util.Set.of("1", "2").contains(customerOrganization)
                || !java.util.Set.of("1", "2").contains(paymentForm)) throw new IllegalArgumentException("Naturaleza o forma de pago invalida.");
    }
    public enum Environment {
        PRODUCTION("1", "https://catalogo-vpfe.dian.gov.co/document/searchqr?documentkey="),
        TEST("2", "https://catalogo-vpfe-hab.dian.gov.co/document/searchqr?documentkey=");
        private final String code, qr;
        Environment(String code, String qr) { this.code = code; this.qr = qr; }
        public String code() { return code; }
        public String qr(String cufe) { return qr + cufe; }
    }
}
