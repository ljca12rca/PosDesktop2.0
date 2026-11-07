package com.posdesktop.pos.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.print.*;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import javax.print.PrintService;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataicoReceiptPrinterTest {
    private HttpServer server;
    private String url;
    private final TestPrinterJob job = new TestPrinterJob();
    private final DataicoReceiptPrinter printer = new DataicoReceiptPrinter(226.8, () -> job);

    @BeforeEach
    void iniciar() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort() + "/factura.pdf";
    }

    @AfterEach
    void cerrar() {
        if (server != null) server.stop(0);
    }

    @Test
    void imprimePdfCompletoEn80mmSinDialogosCentradoNiReduccion() throws Exception {
        responder(pdf());

        assertEquals("Térmica de prueba", printer.imprimir(url, "FEE123"));

        assertTrue(job.printed);
        assertEquals("Factura FEE123", job.name);
        assertEquals(2, job.pages.getNumberOfPages());
        var first = job.pages.getPageFormat(0);
        assertEquals(226.8, first.getWidth(), 0.01);
        assertEquals(300, first.getHeight(), 0.01);
        assertEquals(0, first.getImageableX());
        assertEquals(0, first.getImageableY());
        assertEquals(220, job.pages.getPageFormat(1).getHeight(), 0.01);
        // El rectángulo conserva posición y tamaño originales en puntos.
        assertEquals(Color.BLACK.getRGB(), job.firstImage.getRGB(5, 12));
        assertEquals(Color.BLACK.getRGB(), job.firstImage.getRGB(32, 12));
        assertEquals(Color.WHITE.getRGB(), job.firstImage.getRGB(36, 12));
    }

    @Test
    void informaAusenciaDeImpresoraSinDescargar() {
        job.service = null;
        PrinterException error = assertThrows(PrinterException.class, () -> printer.imprimir(url, "FEE123"));
        assertTrue(error.getMessage().contains("impresora predeterminada"));
        assertFalse(job.printed);
    }

    @Test
    void noEnviaHtmlNiPdfCorruptoALaImpresora() {
        responder("%PDF-1.7\ncontenido corrupto".getBytes(StandardCharsets.US_ASCII));
        assertThrows(java.io.IOException.class, () -> printer.imprimir(url, "FEE123"));
        assertFalse(job.printed);
    }

    @Test
    void propagaElFalloDelSpoolerSinReportarExito() throws Exception {
        responder(pdf());
        job.fail = true;
        assertEquals("Cola no disponible", assertThrows(PrinterException.class,
                () -> printer.imprimir(url, "FEE123")).getMessage());
        assertFalse(job.printed);
    }

    private void responder(byte[] bytes) {
        server.createContext("/factura.pdf", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
        });
    }

    private byte[] pdf() throws Exception {
        try (var document = new PDDocument(); var output = new ByteArrayOutputStream()) {
            var first = new PDPage(new PDRectangle(220, 300));
            document.addPage(first);
            try (var stream = new PDPageContentStream(document, first)) {
                stream.setNonStrokingColor(Color.BLACK);
                stream.addRect(4, 280, 30, 10);
                stream.fill();
            }
            var second = new PDPage(new PDRectangle(220, 440));
            second.setRotation(90);
            document.addPage(second);
            document.save(output);
            return output.toByteArray();
        }
    }

    private static final class TestPrinterJob extends PrinterJob {
        PrintService service = (PrintService) Proxy.newProxyInstance(PrintService.class.getClassLoader(),
                new Class<?>[]{PrintService.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getName")) return "Térmica de prueba";
                    throw new AssertionError("Consulta inesperada: " + method.getName());
                });
        Pageable pages;
        String name;
        boolean printed;
        boolean fail;
        BufferedImage firstImage;

        @Override public PrintService getPrintService() { return service; }
        @Override public void setPageable(Pageable pages) { this.pages = pages; }
        @Override public void print() throws PrinterException {
            if (fail) throw new PrinterException("Cola no disponible");
            for (int page = 0; page < pages.getNumberOfPages(); page++) {
                var image = new BufferedImage(227, 500, BufferedImage.TYPE_INT_RGB);
                var graphics = image.createGraphics();
                try {
                    graphics.setColor(Color.WHITE);
                    graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                    assertEquals(Printable.PAGE_EXISTS, pages.getPrintable(page).print(graphics, pages.getPageFormat(page), page));
                } finally { graphics.dispose(); }
                if (page == 0) firstImage = image;
            }
            printed = true;
        }
        @Override public void setJobName(String name) { this.name = name; }
        @Override public String getJobName() { return name; }
        @Override public PageFormat defaultPage(PageFormat page) { return page; }
        @Override public PageFormat validatePage(PageFormat page) { return page; }
        @Override public boolean printDialog() { throw new AssertionError("No debe abrir diálogo"); }
        @Override public PageFormat pageDialog(PageFormat page) { throw new AssertionError("No debe abrir diálogo"); }
        @Override public void setPrintable(Printable printable) { throw new AssertionError(); }
        @Override public void setPrintable(Printable printable, PageFormat format) { throw new AssertionError(); }
        @Override public void setCopies(int copies) { }
        @Override public int getCopies() { return 1; }
        @Override public String getUserName() { return "Prueba"; }
        @Override public void cancel() { }
        @Override public boolean isCancelled() { return false; }
    }
}
