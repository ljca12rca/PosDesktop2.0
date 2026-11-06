package com.posdesktop.pos.invoicing.domain.dian;

import com.posdesktop.pos.invoicing.domain.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

/** Anexo 1.9, secciones 11.2 y 11.8. Nunca retorna la cadena que contiene secretos. */
public final class DianIdentifiers {
    private DianIdentifiers() { }
    public record CufeInput(String number, String date, String time, BigDecimal net,
            BigDecimal vat, BigDecimal consumption, BigDecimal municipal, BigDecimal payable,
            String issuerNit, String customerId, String environment) { }
    public static String cufe(CufeInput input, DianSecrets secrets) {
        MessageDigest digest = sha384();
        update(digest, input.number(), input.date(), input.time(), amount(input.net()), "01", amount(input.vat()),
                "04", amount(input.consumption()), "03", amount(input.municipal()), amount(input.payable()),
                input.issuerNit(), input.customerId());
        secrets.update(digest, true);
        update(digest, input.environment());
        return HexFormat.of().formatHex(digest.digest());
    }
    public static String cufe(FiscalInvoice invoice, InvoiceCalculator.Totals totals, DianProfile profile, DianSecrets secrets) {
        return cufe(new CufeInput(invoice.number(), invoice.issuedAt().toLocalDate().toString(),
                invoice.issuedAt().format(DateTimeFormatter.ofPattern("HH:mm:ssXXX")), totals.net(),
                tax(totals, "01"), tax(totals, "04"), tax(totals, "03"), totals.payable(),
                invoice.issuer().identification(), invoice.customer().identification(), profile.environment().code()), secrets);
    }
    public static String softwareSecurityCode(String softwareId, String invoiceNumber, DianSecrets secrets) {
        MessageDigest digest = sha384();
        update(digest, softwareId); secrets.update(digest, false); update(digest, invoiceNumber);
        return HexFormat.of().formatHex(digest.digest());
    }
    /** Anexo 1.9, 11.4.3–11.4.6: notas usan PIN, no clave tecnica. */
    public static String cude(CufeInput input,DianSecrets secrets) {
        MessageDigest digest=sha384();
        update(digest,input.number(),input.date(),input.time(),amount(input.net()),"01",amount(input.vat()),"04",amount(input.consumption()),"03",amount(input.municipal()),amount(input.payable()),input.issuerNit(),input.customerId());
        secrets.update(digest,false);update(digest,input.environment());return HexFormat.of().formatHex(digest.digest());
    }
    public static String cude(FiscalInvoice note,InvoiceCalculator.Totals totals,DianProfile profile,DianSecrets secrets) {
        return cude(new CufeInput(note.number(),note.issuedAt().toLocalDate().toString(),note.issuedAt().format(DateTimeFormatter.ofPattern("HH:mm:ssXXX")),totals.net(),tax(totals,"01"),tax(totals,"04"),tax(totals,"03"),totals.payable(),note.issuer().identification(),note.customer().identification(),profile.environment().code()),secrets);
    }
    private static BigDecimal tax(InvoiceCalculator.Totals totals, String scheme) {
        return totals.lines().stream().flatMap(l -> l.taxes().stream()).filter(t -> scheme.equals(t.tax().schemeId()))
                .map(InvoiceCalculator.TaxAmount::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private static String amount(BigDecimal value) {
        if (value.signum() < 0) throw new IllegalArgumentException("CUFE no admite importes negativos.");
        return value.setScale(2, RoundingMode.DOWN).toPlainString();
    }
    private static void update(MessageDigest digest, String... values) {
        for (String value : values) digest.update(value.getBytes(StandardCharsets.UTF_8));
    }
    private static MessageDigest sha384() {
        try { return MessageDigest.getInstance("SHA-384"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
