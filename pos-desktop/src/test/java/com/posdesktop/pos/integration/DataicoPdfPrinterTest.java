package com.posdesktop.pos.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.posdesktop.pos.printing.ReceiptPaper;
import com.sun.net.httpserver.HttpServer;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.PrinterException;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import javax.print.PrintService;
import javax.print.attribute.standard.PrinterIsAcceptingJobs;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

class DataicoPdfPrinterTest {
    private PrintService printer(String name, boolean accepting) {
        return (PrintService) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{PrintService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getName", "toString" -> name;
                    case "getAttribute" -> args[0] == PrinterIsAcceptingJobs.class
                            ? (accepting ? PrinterIsAcceptingJobs.ACCEPTING_JOBS : PrinterIsAcceptingJobs.NOT_ACCEPTING_JOBS) : null;
                    default -> null;
                });
    }

    @Test
    void eligePredeterminadaOUnicaDisponibleSinImpresorasVirtuales() throws Exception {
        var a = printer("Termica", true);
        var b = printer("Oficina", true);
        var pdf = printer("Microsoft Print to PDF", true);
        var offline = printer("Desconectada", false);
        assertSame(b, DataicoPdfPrinter.selectPrinter(new PrintService[]{a, b}, b));
        assertSame(a, DataicoPdfPrinter.selectPrinter(new PrintService[]{a, pdf, offline}, pdf));
        assertThrows(PrinterException.class, () -> DataicoPdfPrinter.selectPrinter(new PrintService[]{a, b}, null));
        assertThrows(PrinterException.class, () -> DataicoPdfPrinter.selectPrinter(new PrintService[]{pdf, offline}, pdf));
    }

    @Test
    void usaElMismoAnchoDelPosSinMargenesArtificialesNiRecorteLateral() throws Exception {
        try (PDDocument document = new PDDocument()) {
            float width = (float) ReceiptPaper.WIDTH_POINTS;
            PDPage page = new PDPage(new PDRectangle(width, 400));
            document.addPage(page);
            try (var content = new PDPageContentStream(document, page)) {
                content.setNonStrokingColor(Color.BLACK);
                content.addRect(4, 386, 10, 10);
                content.addRect(width - 14, 386, 10, 10);
                content.fill();
            }
            var pages = DataicoPdfPrinter.actualSizePages(document, format -> format);
            PageFormat format = pages.getPageFormat(0);
            assertEquals(ReceiptPaper.WIDTH_POINTS, format.getWidth(), 0.001);
            assertEquals(400, format.getHeight(), 0.001);
            assertEquals(0, format.getImageableX(), 0.001);
            assertEquals(0, format.getImageableY(), 0.001);
            assertEquals(format.getWidth(), format.getImageableWidth(), 0.001);
            var image = new BufferedImage(227, 400, BufferedImage.TYPE_INT_RGB);
            var graphics = image.createGraphics();
            try {
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, 227, 400);
                assertEquals(0, pages.getPrintable(0).print(graphics, format, 0));
                assertEquals(0, image.getRGB(5, 5) & 0xFFFFFF);
                assertEquals(0, image.getRGB(215, 5) & 0xFFFFFF);
                assertEquals(0xFFFFFF, image.getRGB(100, 5) & 0xFFFFFF);
            } finally { graphics.dispose(); }
        }
    }

    @Test
    void conservaLargoPorPaginaYRespetaMargenesFisicosValidados() throws Exception {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(new PDRectangle(226.8f, 600)));
            document.addPage(new PDPage(new PDRectangle(226.8f, 350)));
            PDPage rotated = new PDPage(new PDRectangle(450, 226.8f));
            rotated.setRotation(90);
            document.addPage(rotated);
            var pages = DataicoPdfPrinter.actualSizePages(document, format -> {
                Paper paper = format.getPaper();
                paper.setImageableArea(3, 4, paper.getWidth() - 6, paper.getHeight() - 8);
                format.setPaper(paper);
                return format;
            });
            assertEquals(3, pages.getNumberOfPages());
            for (int i = 0; i < 3; i++) {
                var format = pages.getPageFormat(i);
                assertEquals(new double[]{600, 350, 450}[i], format.getHeight(), 0.001);
                assertEquals(ReceiptPaper.WIDTH_POINTS, format.getWidth(), 0.001);
                assertEquals(3, format.getImageableX(), 0.001);
                assertEquals(4, format.getImageableY(), 0.001);
            }
        }
    }

    @Test
    void renderizaUnPuntoPdfComoUnPuntoDeImpresionSinReducirNiAmpliar() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(300, 300));
            document.addPage(page);
            try (var content = new PDPageContentStream(document, page)) {
                content.setNonStrokingColor(Color.BLACK);
                content.addRect(10, 270, 72, 20);
                content.fill();
            }
            // Papel menor Y mayor al PDF: ambos deben conservar la barra de 72 puntos.
            for (int size : new int[]{150, 600}) {
                BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
                var graphics = image.createGraphics();
                try {
                    graphics.setColor(Color.WHITE);
                    graphics.fillRect(0, 0, size, size);
                    Paper paper = new Paper();
                    paper.setSize(size, size);
                    paper.setImageableArea(0, 0, size, size);
                    PageFormat format = new PageFormat();
                    format.setPaper(paper);
                    assertEquals(0, DataicoPdfPrinter.actualSizePrintable(document).print(graphics, format, 0));
                    int dark = 0;
                    for (int x = 0; x < size; x++) if ((image.getRGB(x, 20) & 0xFFFFFF) == 0) dark++;
                    assertEquals(72, dark, 1);
                    assertEquals(1, DataicoPdfPrinter.actualSizePrintable(document).print(graphics, format, 1));
                } finally {
                    graphics.dispose();
                }
            }
        }
    }

    @Test
    void descargaEnTemporalEnviaUnaVezYLimpiaInclusoSiFallaLaCola() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] bytes = "%PDF-1.7\noriginal".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        server.createContext("/factura", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
        server.start();
        try {
            var printer = printer("Termica", true);
            var allowed = new java.util.concurrent.atomic.AtomicBoolean(true);
            var submitted = new java.util.concurrent.atomic.AtomicInteger();
            server.createContext("/cancelada", exchange -> {
                allowed.set(false); // Se desmarca mientras se espera la descarga.
                try (exchange) {
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            });
            var cancelable = new DataicoPdfPrinter(() -> new PrintService[]{printer}, () -> printer,
                    (pdf, selected, job, permission) -> submitted.incrementAndGet());
            String cancelledUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/cancelada";
            assertThrows(java.util.concurrent.CancellationException.class,
                    () -> cancelable.imprimir(cancelledUrl, "FEE123", allowed::get));
            assertEquals(0, submitted.get());
            assertThrows(java.util.concurrent.CancellationException.class,
                    () -> cancelable.imprimir("invalid", "FEE123", () -> false));
            AtomicReference<Path> temporary = new AtomicReference<>();
            for (boolean fail : new boolean[]{false, true}) {
                var service = new DataicoPdfPrinter(() -> new PrintService[]{printer}, () -> printer,
                        (pdf, selected, job, permission) -> {
                            temporary.set(pdf);
                            assertArrayEquals(bytes, Files.readAllBytes(pdf));
                            assertSame(printer, selected);
                            assertEquals("Factura Dataico FEE123", job);
                            if (fail) throw new PrinterException("Cola no disponible");
                        });
                String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/factura";
                if (fail) assertThrows(PrinterException.class, () -> service.imprimir(url, "FEE123"));
                else assertEquals("Termica", service.imprimir(url, "FEE123"));
                assertFalse(Files.exists(temporary.get()));
                assertFalse(Files.exists(temporary.get().getParent()));
            }
        } finally {
            server.stop(0);
        }
    }
}
