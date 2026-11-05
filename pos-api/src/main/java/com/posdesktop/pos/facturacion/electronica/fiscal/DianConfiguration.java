package com.posdesktop.pos.facturacion.electronica.fiscal;

import com.posdesktop.pos.invoicing.domain.FiscalInvoice;
import com.posdesktop.pos.invoicing.domain.dian.*;
import java.util.UUID;

/** Solo datos publicos. PRUEBAS y PRODUCCION se guardan en filas distintas. */
public record DianConfiguration(String softwareName, UUID softwareId, UUID testSetId, String providerNit,
        String providerDv, FiscalInvoice.Party issuer, String issuerOrganization, FiscalInvoice.Numbering numbering,
        long previewNumber) {
    public void validate() {
        if (softwareName == null || softwareName.isBlank() || softwareName.length() > 120 || issuer == null || numbering == null
                || softwareId == null || providerNit == null || providerDv == null
                || previewNumber < numbering.rangeFrom() || previewNumber > numbering.rangeUntil()
                || numbering.prefix().length() > 20 || !numbering.prefix().matches("[A-Za-z0-9]*")
                || !numbering.resolution().matches("[0-9]{1,40}"))
            throw new IllegalArgumentException("Revisa emisor, software, resolucion, rango y numero de previsualizacion.");
        profile("PRUEBAS", "2", "1");
        if (!"31".equals(issuer.identificationType()) || !NitCheckDigit.calculate(issuer.identification()).equals(issuer.checkDigit()))
            throw new IllegalArgumentException("El emisor requiere NIT y DV correctos.");
        var catalogs = new DianCatalogs();
        for (String duty : issuer.taxResponsibilities().split(";", -1)) catalogs.require("responsibility", duty);
        catalogs.require("party-tax", issuer.taxSchemeId());
        catalogs.require("country", issuer.address().countryCode());
        catalogs.require("department", issuer.address().departmentCode());
        catalogs.require("municipality", issuer.address().cityCode());
        if (!"CO".equals(issuer.address().countryCode()) || !issuer.address().cityCode().startsWith(issuer.address().departmentCode()))
            throw new IllegalArgumentException("El domicilio del emisor debe corresponder a un municipio de Colombia.");
    }
    public DianProfile profile(String environment, String customerOrganization, String paymentForm) {
        return new DianProfile(environment.equals("PRODUCCION") ? DianProfile.Environment.PRODUCTION : DianProfile.Environment.TEST,
                softwareId, providerNit, providerDv, issuerOrganization, customerOrganization, paymentForm);
    }
}
