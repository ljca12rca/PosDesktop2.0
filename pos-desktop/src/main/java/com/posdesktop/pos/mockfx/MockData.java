package com.posdesktop.pos.mockfx;

import java.util.List;

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
}
