package com.posdesktop.pos.mockfx;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class MockData {

    private static final Locale COLOMBIA_LOCALE = Locale.forLanguageTag("es-CO");
    private static final DateTimeFormatter SHORT_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final NumberFormat CURRENCY_FORMAT = createCurrencyFormat();

    private static final List<ProviderMockRow> PROVIDER_SEED = List.of(
            new ProviderMockRow("PV-001", "Maderas del Norte", "Insumos hogar", "Lina Romero", "320 555 1120", "$ 4.280.000", "Activo", 3),
            new ProviderMockRow("PV-002", "Electro Andina", "Electrodomesticos", "Camilo Diaz", "315 220 9081", "$ 2.460.000", "Activo", 2),
            new ProviderMockRow("PV-003", "Deco Espacios SAS", "Decoracion", "Paula Perez", "317 890 4430", "$ 980.000", "Seguimiento", 1),
            new ProviderMockRow("PV-004", "Textiles Aurora", "Textiles", "Sandra Ruiz", "310 443 2277", "$ 1.540.000", "Activo", 2)
    );
    private static final List<SupplierInvoiceMockRow> SUPPLIER_INVOICE_SEED = List.of(
            new SupplierInvoiceMockRow("FC-001", "PV-001", "FAC-9031", "Tableros MDF y herrajes", "03 Jul 2026", "18 Jul 2026", "$ 2.100.000", "$ 600.000", "$ 1.500.000", "Pendiente"),
            new SupplierInvoiceMockRow("FC-002", "PV-001", "FAC-9058", "Bisagras premium", "06 Jul 2026", "22 Jul 2026", "$ 1.480.000", "$ 320.000", "$ 1.160.000", "Pendiente"),
            new SupplierInvoiceMockRow("FC-003", "PV-001", "FAC-9074", "Cantos y adhesivos", "08 Jul 2026", "25 Jul 2026", "$ 1.620.000", "$ 0", "$ 1.620.000", "Pendiente"),
            new SupplierInvoiceMockRow("FC-004", "PV-002", "EL-1201", "Licuadoras y cafeteras", "04 Jul 2026", "19 Jul 2026", "$ 1.300.000", "$ 300.000", "$ 1.000.000", "Pendiente"),
            new SupplierInvoiceMockRow("FC-005", "PV-002", "EL-1209", "Freidoras compactas", "07 Jul 2026", "24 Jul 2026", "$ 1.460.000", "$ 0", "$ 1.460.000", "Pendiente"),
            new SupplierInvoiceMockRow("FC-006", "PV-003", "DE-778", "Cortinas blackout importadas", "02 Jul 2026", "16 Jul 2026", "$ 980.000", "$ 0", "$ 980.000", "Pendiente"),
            new SupplierInvoiceMockRow("FC-007", "PV-004", "TX-441", "Juego de manteles premium", "01 Jul 2026", "15 Jul 2026", "$ 860.000", "$ 200.000", "$ 660.000", "Pendiente"),
            new SupplierInvoiceMockRow("FC-008", "PV-004", "TX-455", "Linos decorativos", "05 Jul 2026", "21 Jul 2026", "$ 880.000", "$ 0", "$ 880.000", "Pendiente")
    );
    private static final List<SupplierPaymentMockRow> SUPPLIER_PAYMENT_SEED = List.of(
            new SupplierPaymentMockRow("AB-001", "FC-001", "05 Jul 2026", "$ 350.000", "Transferencia", "comp_pago_9031.jpg"),
            new SupplierPaymentMockRow("AB-002", "FC-001", "08 Jul 2026", "$ 250.000", "Efectivo", "recibo_caja_411.jpg"),
            new SupplierPaymentMockRow("AB-003", "FC-002", "07 Jul 2026", "$ 320.000", "Transferencia", "trf_9058.pdf"),
            new SupplierPaymentMockRow("AB-004", "FC-004", "06 Jul 2026", "$ 300.000", "Transferencia", "abono_el1201.jpg"),
            new SupplierPaymentMockRow("AB-005", "FC-007", "03 Jul 2026", "$ 200.000", "Efectivo", "recibo_tx441.jpg")
    );
    private static final List<SupplierSupportMockRow> SUPPLIER_SUPPORT_SEED = List.of(
            new SupplierSupportMockRow("SP-001", "FC-001", "Factura proveedor", "factura_mdf_9031.pdf", "PDF", "03 Jul 2026", "Cargado"),
            new SupplierSupportMockRow("SP-002", "FC-001", "Comprobante abono", "comp_pago_9031.jpg", "JPG", "05 Jul 2026", "Verificado"),
            new SupplierSupportMockRow("SP-003", "FC-001", "Comprobante abono", "recibo_caja_411.jpg", "JPG", "08 Jul 2026", "Pendiente"),
            new SupplierSupportMockRow("SP-004", "FC-002", "Factura proveedor", "factura_bisagras_9058.jpg", "JPG", "06 Jul 2026", "Cargado"),
            new SupplierSupportMockRow("SP-005", "FC-002", "Comprobante abono", "trf_9058.pdf", "PDF", "07 Jul 2026", "Verificado"),
            new SupplierSupportMockRow("SP-006", "FC-004", "Factura proveedor", "electro_1201.pdf", "PDF", "04 Jul 2026", "Cargado"),
            new SupplierSupportMockRow("SP-007", "FC-004", "Comprobante abono", "abono_el1201.jpg", "JPG", "06 Jul 2026", "Verificado"),
            new SupplierSupportMockRow("SP-008", "FC-006", "Factura proveedor", "deco_778.jpg", "JPG", "02 Jul 2026", "Pendiente"),
            new SupplierSupportMockRow("SP-009", "FC-007", "Factura proveedor", "tx_441.pdf", "PDF", "01 Jul 2026", "Cargado"),
            new SupplierSupportMockRow("SP-010", "FC-007", "Comprobante abono", "recibo_tx441.jpg", "JPG", "03 Jul 2026", "Verificado")
    );

    private static final List<ProviderMockRow> providerStore = new ArrayList<>();
    private static final List<SupplierInvoiceMockRow> invoiceStore = new ArrayList<>();
    private static final List<SupplierPaymentMockRow> paymentStore = new ArrayList<>();
    private static final List<SupplierSupportMockRow> supportStore = new ArrayList<>();

    static {
        resetInvoiceMocks();
    }

    private MockData() {
    }

    public record SaleRow(String articulo, String categoria, String cantidad, String valorUnitario, String total) {
    }

    public record CloseRow(String fecha, String ventas, String abonos, String base, String egresos, String total) {
    }

    public record LayawayRow(String numero, String cliente, String articulo, String abonado, String restante, String fecha) {
    }

    public record MovementRow(String numero, String origen, String total, String recibido, String devuelto, String fecha) {
    }

    public record PaymentRow(String fechaHora, String valor, String canal, String referencia) {
    }

    public record ProviderMockRow(
            String id,
            String nombre,
            String categoria,
            String contacto,
            String telefono,
            String saldoPendiente,
            String estado,
            int facturasActivas
    ) {
    }

    public record SupplierInvoiceMockRow(
            String id,
            String proveedorId,
            String numero,
            String concepto,
            String fechaRegistro,
            String vencimiento,
            String valorTotal,
            String abonado,
            String saldo,
            String estado
    ) {
    }

    public record SupplierPaymentMockRow(
            String id,
            String facturaId,
            String fecha,
            String valor,
            String medio,
            String soporte
    ) {
    }

    public record SupplierSupportMockRow(
            String id,
            String facturaId,
            String tipo,
            String archivo,
            String formato,
            String fechaCarga,
            String estado
    ) {
    }

    public static List<SaleRow> sales() {
        return List.of(
                new SaleRow("Combo oficina", "Manual", "1", "$ 120.000", "$ 120.000"),
                new SaleRow("Accesorio premium", "Separado", "1", "$ 45.000", "$ 45.000"),
                new SaleRow("Servicio rapido", "Manual", "2", "$ 18.000", "$ 36.000")
        );
    }

    public static List<CloseRow> closes() {
        return List.of(
                new CloseRow("06 Jul 2026", "$ 3.420.000", "$ 340.000", "$ 180.000", "$ 90.000", "$ 3.670.000"),
                new CloseRow("05 Jul 2026", "$ 2.950.000", "$ 210.000", "$ 180.000", "$ 75.000", "$ 3.265.000"),
                new CloseRow("04 Jul 2026", "$ 3.180.000", "$ 290.000", "$ 180.000", "$ 120.000", "$ 3.530.000")
        );
    }

    public static List<LayawayRow> layaways() {
        return List.of(
                new LayawayRow("SP-103", "Amparo Alvarez", "Cortinas blackout", "$ 180.000", "$ 60.000", "06 Jul 2026"),
                new LayawayRow("SP-104", "Sandra Mesa", "Juego de comedor", "$ 220.000", "$ 95.000", "06 Jul 2026"),
                new LayawayRow("SP-105", "Jorge Paez", "Vitrina modular", "$ 90.000", "$ 150.000", "05 Jul 2026"),
                new LayawayRow("SP-106", "Nubia Rojas", "Licuadora pro", "$ 20.000", "$ 70.000", "05 Jul 2026")
        );
    }

    public static List<MovementRow> movements() {
        return List.of(
                new MovementRow("MV-901", "Venta mostrador", "$ 120.000", "$ 150.000", "$ 30.000", "06 Jul 2026 09:12"),
                new MovementRow("MV-902", "Abono separado", "$ 45.000", "$ 50.000", "$ 5.000", "06 Jul 2026 10:45"),
                new MovementRow("MV-903", "Venta manual", "$ 36.000", "$ 40.000", "$ 4.000", "06 Jul 2026 11:03"),
                new MovementRow("MV-904", "Venta rapida", "$ 90.000", "$ 100.000", "$ 10.000", "06 Jul 2026 11:42")
        );
    }

    public static List<PaymentRow> payments() {
        return List.of(
                new PaymentRow("06 Jul 2026 08:15", "$ 20.000", "Efectivo", "Inicial"),
                new PaymentRow("06 Jul 2026 12:28", "$ 30.000", "Transferencia", "TRF-9031"),
                new PaymentRow("07 Jul 2026 15:10", "$ 10.000", "Efectivo", "Caja 1")
        );
    }

    public static synchronized void resetInvoiceMocks() {
        providerStore.clear();
        providerStore.addAll(PROVIDER_SEED);
        invoiceStore.clear();
        invoiceStore.addAll(SUPPLIER_INVOICE_SEED);
        paymentStore.clear();
        paymentStore.addAll(SUPPLIER_PAYMENT_SEED);
        supportStore.clear();
        supportStore.addAll(SUPPLIER_SUPPORT_SEED);
    }

    public static synchronized List<ProviderMockRow> providers() {
        return providerStore.stream()
                .map(MockData::rebuildProvider)
                .toList();
    }

    public static synchronized Optional<ProviderMockRow> findProvider(String proveedorId) {
        return providers().stream()
                .filter(provider -> provider.id().equals(proveedorId))
                .findFirst();
    }

    public static synchronized ProviderMockRow createProvider(
            String nombre,
            String categoria,
            String contacto,
            String telefono
    ) {
        String providerName = requiredText(nombre, "Debes ingresar el nombre del proveedor.");
        ProviderMockRow provider = new ProviderMockRow(
                nextSequentialId(providerStore.stream().map(ProviderMockRow::id).toList(), "PV-"),
                providerName,
                safeText(categoria, "General"),
                safeText(contacto, "Sin contacto"),
                safeText(telefono, "-"),
                formatCurrency(BigDecimal.ZERO),
                "Nuevo",
                0
        );
        providerStore.add(provider);
        return rebuildProvider(provider);
    }

    public static synchronized List<SupplierInvoiceMockRow> supplierInvoices() {
        return invoiceStore.stream()
                .map(MockData::rebuildInvoice)
                .toList();
    }

    public static synchronized Optional<SupplierInvoiceMockRow> findInvoice(String facturaId) {
        return supplierInvoices().stream()
                .filter(invoice -> invoice.id().equals(facturaId))
                .findFirst();
    }

    public static synchronized SupplierInvoiceMockRow createSupplierInvoice(
            String proveedorId,
            String numeroFactura,
            String concepto,
            LocalDate fechaVencimiento,
            BigDecimal valorTotal,
            String soporteFormato,
            String soporteArchivo
    ) {
        ProviderMockRow provider = findProvider(proveedorId)
                .orElseThrow(() -> new IllegalArgumentException("El proveedor seleccionado no existe en el mock."));
        if (fechaVencimiento == null) {
            throw new IllegalArgumentException("Debes seleccionar la fecha de vencimiento.");
        }
        if (valorTotal == null || valorTotal.signum() <= 0) {
            throw new IllegalArgumentException("El valor de la factura debe ser mayor a cero.");
        }

        String invoiceId = nextSequentialId(invoiceStore.stream().map(SupplierInvoiceMockRow::id).toList(), "FC-");
        String invoiceNumber = safeText(numeroFactura, buildInvoiceNumber(provider, invoiceId));
        SupplierInvoiceMockRow invoice = new SupplierInvoiceMockRow(
                invoiceId,
                provider.id(),
                invoiceNumber,
                requiredText(concepto, "Debes ingresar el concepto de la factura."),
                formatDate(LocalDate.now()),
                formatDate(fechaVencimiento),
                formatCurrency(valorTotal),
                formatCurrency(BigDecimal.ZERO),
                formatCurrency(valorTotal),
                "Pendiente"
        );
        invoiceStore.add(invoice);

        String supportFormatValue = normalizeSupportFormat(soporteFormato);
        String supportFileName = resolveSupportFileName(
                soporteArchivo,
                "factura_" + normalizeFileToken(invoiceNumber),
                supportFormatValue
        );
        if (!supportFileName.isBlank()) {
            supportStore.add(new SupplierSupportMockRow(
                    nextSequentialId(supportStore.stream().map(SupplierSupportMockRow::id).toList(), "SP-"),
                    invoiceId,
                    "Factura proveedor",
                    supportFileName,
                    supportFormatValue,
                    formatDate(LocalDate.now()),
                    "Cargado"
            ));
        }

        return rebuildInvoice(invoice);
    }

    public static synchronized List<SupplierPaymentMockRow> supplierPayments() {
        return List.copyOf(paymentStore);
    }

    public static synchronized SupplierPaymentMockRow createSupplierPayment(
            String facturaId,
            BigDecimal valorAbono,
            String medio,
            String soporteFormato,
            String soporteArchivo
    ) {
        SupplierInvoiceMockRow invoice = findInvoice(facturaId)
                .orElseThrow(() -> new IllegalArgumentException("La factura seleccionada no existe en el mock."));
        if (valorAbono == null || valorAbono.signum() <= 0) {
            throw new IllegalArgumentException("El valor del abono debe ser mayor a cero.");
        }

        BigDecimal currentBalance = parseCurrency(invoice.saldo());
        if (valorAbono.compareTo(currentBalance) > 0) {
            throw new IllegalArgumentException("El abono no puede superar el saldo actual de la factura.");
        }

        String paymentId = nextSequentialId(paymentStore.stream().map(SupplierPaymentMockRow::id).toList(), "AB-");
        String supportFormatValue = normalizeSupportFormat(soporteFormato);
        String paymentSupport = resolveSupportFileName(
                soporteArchivo,
                "abono_" + normalizeFileToken(invoice.numero()) + "_" + paymentId.toLowerCase(Locale.ROOT),
                supportFormatValue
        );
        SupplierPaymentMockRow payment = new SupplierPaymentMockRow(
                paymentId,
                invoice.id(),
                formatDate(LocalDate.now()),
                formatCurrency(valorAbono),
                safeText(medio, "Transferencia"),
                paymentSupport
        );
        paymentStore.add(payment);
        supportStore.add(new SupplierSupportMockRow(
                nextSequentialId(supportStore.stream().map(SupplierSupportMockRow::id).toList(), "SP-"),
                invoice.id(),
                "Comprobante abono",
                paymentSupport,
                supportFormatValue,
                formatDate(LocalDate.now()),
                "Verificado"
        ));
        return payment;
    }

    public static synchronized List<SupplierSupportMockRow> supplierSupports() {
        return List.copyOf(supportStore);
    }

    public static synchronized SupplierSupportMockRow createSupplierSupport(
            String facturaId,
            String tipo,
            String archivo,
            String formato
    ) {
        SupplierInvoiceMockRow invoice = findInvoice(facturaId)
                .orElseThrow(() -> new IllegalArgumentException("La factura seleccionada no existe en el mock."));
        SupplierSupportMockRow support = new SupplierSupportMockRow(
                nextSequentialId(supportStore.stream().map(SupplierSupportMockRow::id).toList(), "SP-"),
                invoice.id(),
                safeText(tipo, "Factura proveedor"),
                requiredText(archivo, "Debes ingresar el nombre del archivo soporte."),
                normalizeSupportFormat(formato),
                formatDate(LocalDate.now()),
                "Cargado"
        );
        supportStore.add(support);
        return support;
    }

    public static synchronized List<SupplierInvoiceMockRow> invoicesByProvider(String proveedorId) {
        if (proveedorId == null || proveedorId.isBlank()) {
            return supplierInvoices();
        }
        return supplierInvoices().stream()
                .filter(invoice -> proveedorId.equals(invoice.proveedorId()))
                .toList();
    }

    public static synchronized List<SupplierPaymentMockRow> paymentsByInvoice(String facturaId) {
        if (facturaId == null || facturaId.isBlank()) {
            return List.of();
        }
        return paymentStore.stream()
                .filter(payment -> facturaId.equals(payment.facturaId()))
                .toList();
    }

    public static synchronized List<SupplierSupportMockRow> supportsByInvoice(String facturaId) {
        if (facturaId == null || facturaId.isBlank()) {
            return List.of();
        }
        return supportStore.stream()
                .filter(support -> facturaId.equals(support.facturaId()))
                .toList();
    }

    public static synchronized Map<String, List<SupplierInvoiceMockRow>> invoicesGroupedByProvider() {
        return supplierInvoices().stream()
                .collect(Collectors.groupingBy(SupplierInvoiceMockRow::proveedorId));
    }

    public static List<String> supportFormats() {
        return List.of("PDF", "JPG", "PNG");
    }

    public static List<String> supportTypes() {
        return List.of("Factura proveedor", "Comprobante abono", "Nota de ajuste");
    }

    public static List<String> paymentMethods() {
        return List.of("Transferencia", "Efectivo", "Nequi", "Tarjeta");
    }

    private static ProviderMockRow rebuildProvider(ProviderMockRow provider) {
        List<SupplierInvoiceMockRow> providerInvoices = invoiceStore.stream()
                .filter(invoice -> provider.id().equals(invoice.proveedorId()))
                .map(MockData::rebuildInvoice)
                .toList();
        BigDecimal balance = providerInvoices.stream()
                .map(invoice -> parseCurrency(invoice.saldo()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int invoiceCount = providerInvoices.size();
        String status = invoiceCount == 0
                ? "Nuevo"
                : provider.estado();
        return new ProviderMockRow(
                provider.id(),
                provider.nombre(),
                provider.categoria(),
                provider.contacto(),
                provider.telefono(),
                formatCurrency(balance),
                status,
                invoiceCount
        );
    }

    private static SupplierInvoiceMockRow rebuildInvoice(SupplierInvoiceMockRow invoice) {
        BigDecimal total = parseCurrency(invoice.valorTotal());
        BigDecimal paid = paymentStore.stream()
                .filter(payment -> invoice.id().equals(payment.facturaId()))
                .map(payment -> parseCurrency(payment.valor()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal balance = total.subtract(paid).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        String state = balance.signum() == 0 ? "Abonada" : "Pendiente";
        return new SupplierInvoiceMockRow(
                invoice.id(),
                invoice.proveedorId(),
                invoice.numero(),
                invoice.concepto(),
                invoice.fechaRegistro(),
                invoice.vencimiento(),
                formatCurrency(total),
                formatCurrency(paid),
                formatCurrency(balance),
                state
        );
    }

    private static String nextSequentialId(List<String> ids, String prefix) {
        int next = ids.stream()
                .filter(id -> id != null && id.startsWith(prefix))
                .map(id -> id.substring(prefix.length()))
                .map(value -> value.replaceAll("[^\\d]", ""))
                .filter(value -> !value.isBlank())
                .mapToInt(Integer::parseInt)
                .max()
                .orElse(0) + 1;
        return prefix + String.format("%03d", next);
    }

    private static String buildInvoiceNumber(ProviderMockRow provider, String invoiceId) {
        String[] parts = safeText(provider.nombre(), "FAC").split("\\s+");
        StringBuilder prefix = new StringBuilder();
        for (String part : parts) {
            if (!part.isBlank() && Character.isLetter(part.charAt(0))) {
                prefix.append(Character.toUpperCase(part.charAt(0)));
            }
            if (prefix.length() == 2) {
                break;
            }
        }
        if (prefix.isEmpty()) {
            prefix.append("FC");
        } else if (prefix.length() == 1) {
            prefix.append('X');
        }
        String numeric = invoiceId.replaceAll("[^\\d]", "");
        return prefix + "-" + String.format("%04d", Integer.parseInt(numeric) + 9000);
    }

    private static String requiredText(String value, String message) {
        String normalized = safeText(value, "");
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String normalizeSupportFormat(String format) {
        String normalized = safeText(format, "PDF").toUpperCase(Locale.ROOT);
        return supportFormats().contains(normalized) ? normalized : "PDF";
    }

    private static String resolveSupportFileName(String fileName, String baseName, String format) {
        String extension = normalizeSupportFormat(format).toLowerCase(Locale.ROOT);
        String normalizedName = safeText(fileName, "");
        if (normalizedName.isBlank()) {
            return normalizeFileToken(baseName) + "." + extension;
        }
        if (normalizedName.contains(".")) {
            return normalizedName;
        }
        return normalizedName + "." + extension;
    }

    private static String normalizeFileToken(String value) {
        String normalized = safeText(value, "archivo")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("_+", "_");
        normalized = normalized.replaceAll("^_+", "").replaceAll("_+$", "");
        return normalized.isBlank() ? "archivo" : normalized;
    }

    private static String formatDate(LocalDate date) {
        return SHORT_DATE_FORMATTER.format(date);
    }

    private static BigDecimal parseCurrency(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        String normalized = value
                .replace("$", "")
                .replace("COP", "")
                .replace("\u00A0", "")
                .replace(" ", "")
                .trim()
                .replaceAll("[^\\d,.-]", "");

        int commaCount = normalized.length() - normalized.replace(",", "").length();
        int dotCount = normalized.length() - normalized.replace(".", "").length();
        int lastComma = normalized.lastIndexOf(',');
        int lastDot = normalized.lastIndexOf('.');

        if (commaCount > 0 && dotCount > 0) {
            boolean commaIsDecimal = lastComma > lastDot;
            normalized = commaIsDecimal
                    ? normalized.replace(".", "").replace(",", ".")
                    : normalized.replace(",", "");
        } else if (commaCount > 1) {
            normalized = normalized.replace(",", "");
        } else if (dotCount > 1) {
            normalized = normalized.replace(".", "");
        } else if (commaCount == 1) {
            int digitsAfterComma = normalized.length() - lastComma - 1;
            normalized = digitsAfterComma == 3
                    ? normalized.replace(",", "")
                    : normalized.replace(",", ".");
        } else if (dotCount == 1) {
            int digitsAfterDot = normalized.length() - lastDot - 1;
            if (digitsAfterDot == 3) {
                normalized = normalized.replace(".", "");
            }
        }
        return new BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP);
    }

    private static String formatCurrency(BigDecimal value) {
        return CURRENCY_FORMAT.format(value == null ? BigDecimal.ZERO : value);
    }

    private static NumberFormat createCurrencyFormat() {
        NumberFormat format = NumberFormat.getCurrencyInstance(COLOMBIA_LOCALE);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(0);
        return format;
    }
}
