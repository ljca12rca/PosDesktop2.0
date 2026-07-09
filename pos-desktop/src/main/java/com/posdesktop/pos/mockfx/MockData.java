package com.posdesktop.pos.mockfx;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class MockData {

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

    public static List<ProviderMockRow> providers() {
        return List.of(
                new ProviderMockRow("PV-001", "Maderas del Norte", "Insumos hogar", "Lina Romero", "320 555 1120", "$ 4.280.000", "Activo", 3),
                new ProviderMockRow("PV-002", "Electro Andina", "Electrodomesticos", "Camilo Diaz", "315 220 9081", "$ 2.460.000", "Activo", 2),
                new ProviderMockRow("PV-003", "Deco Espacios SAS", "Decoracion", "Paula Perez", "317 890 4430", "$ 980.000", "Seguimiento", 1),
                new ProviderMockRow("PV-004", "Textiles Aurora", "Textiles", "Sandra Ruiz", "310 443 2277", "$ 1.540.000", "Activo", 2)
        );
    }

    public static List<SupplierInvoiceMockRow> supplierInvoices() {
        return List.of(
                new SupplierInvoiceMockRow("FC-001", "PV-001", "FAC-9031", "Tableros MDF y herrajes", "03 Jul 2026", "18 Jul 2026", "$ 2.100.000", "$ 600.000", "$ 1.500.000", "Pendiente"),
                new SupplierInvoiceMockRow("FC-002", "PV-001", "FAC-9058", "Bisagras premium", "06 Jul 2026", "22 Jul 2026", "$ 1.480.000", "$ 320.000", "$ 1.160.000", "Abonada"),
                new SupplierInvoiceMockRow("FC-003", "PV-001", "FAC-9074", "Cantos y adhesivos", "08 Jul 2026", "25 Jul 2026", "$ 1.620.000", "$ 0", "$ 1.620.000", "Pendiente"),
                new SupplierInvoiceMockRow("FC-004", "PV-002", "EL-1201", "Licuadoras y cafeteras", "04 Jul 2026", "19 Jul 2026", "$ 1.300.000", "$ 300.000", "$ 1.000.000", "Abonada"),
                new SupplierInvoiceMockRow("FC-005", "PV-002", "EL-1209", "Freidoras compactas", "07 Jul 2026", "24 Jul 2026", "$ 1.460.000", "$ 0", "$ 1.460.000", "Pendiente"),
                new SupplierInvoiceMockRow("FC-006", "PV-003", "DE-778", "Cortinas blackout importadas", "02 Jul 2026", "16 Jul 2026", "$ 980.000", "$ 0", "$ 980.000", "Pendiente"),
                new SupplierInvoiceMockRow("FC-007", "PV-004", "TX-441", "Juego de manteles premium", "01 Jul 2026", "15 Jul 2026", "$ 860.000", "$ 200.000", "$ 660.000", "Abonada"),
                new SupplierInvoiceMockRow("FC-008", "PV-004", "TX-455", "Linos decorativos", "05 Jul 2026", "21 Jul 2026", "$ 880.000", "$ 0", "$ 880.000", "Pendiente")
        );
    }

    public static List<SupplierPaymentMockRow> supplierPayments() {
        return List.of(
                new SupplierPaymentMockRow("AB-001", "FC-001", "05 Jul 2026", "$ 350.000", "Transferencia", "comp_pago_9031.jpg"),
                new SupplierPaymentMockRow("AB-002", "FC-001", "08 Jul 2026", "$ 250.000", "Efectivo", "recibo_caja_411.jpg"),
                new SupplierPaymentMockRow("AB-003", "FC-002", "07 Jul 2026", "$ 320.000", "Transferencia", "trf_9058.pdf"),
                new SupplierPaymentMockRow("AB-004", "FC-004", "06 Jul 2026", "$ 300.000", "Transferencia", "abono_el1201.jpg"),
                new SupplierPaymentMockRow("AB-005", "FC-007", "03 Jul 2026", "$ 200.000", "Efectivo", "recibo_tx441.jpg")
        );
    }

    public static List<SupplierSupportMockRow> supplierSupports() {
        return List.of(
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
    }

    public static List<SupplierInvoiceMockRow> invoicesByProvider(String proveedorId) {
        if (proveedorId == null || proveedorId.isBlank()) {
            return supplierInvoices();
        }
        return supplierInvoices().stream()
                .filter(invoice -> proveedorId.equals(invoice.proveedorId()))
                .toList();
    }

    public static List<SupplierPaymentMockRow> paymentsByInvoice(String facturaId) {
        if (facturaId == null || facturaId.isBlank()) {
            return List.of();
        }
        return supplierPayments().stream()
                .filter(payment -> facturaId.equals(payment.facturaId()))
                .toList();
    }

    public static List<SupplierSupportMockRow> supportsByInvoice(String facturaId) {
        if (facturaId == null || facturaId.isBlank()) {
            return List.of();
        }
        return supplierSupports().stream()
                .filter(support -> facturaId.equals(support.facturaId()))
                .toList();
    }

    public static Map<String, List<SupplierInvoiceMockRow>> invoicesGroupedByProvider() {
        return supplierInvoices().stream()
                .collect(Collectors.groupingBy(SupplierInvoiceMockRow::proveedorId));
    }

    public static List<String> supportFormats() {
        return List.of("PDF", "JPG", "PNG");
    }

    public static List<String> supportTypes() {
        return List.of("Factura proveedor", "Comprobante abono", "Nota de ajuste");
    }
}
