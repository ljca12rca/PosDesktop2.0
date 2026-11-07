package com.posdesktop.pos.integration;

import java.awt.print.Book;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.printing.PDFPrintable;
import org.apache.pdfbox.printing.Scaling;

/** Imprime el PDF original de Dataico sin diálogos ni un visor de PDF externo. */
public final class DataicoReceiptPrinter {
    private final Supplier<PrinterJob> printerJobs;
    private final double paperWidthPoints;

    public DataicoReceiptPrinter(double paperWidthPoints) {
        this(paperWidthPoints, PrinterJob::getPrinterJob);
    }

    DataicoReceiptPrinter(double paperWidthPoints, Supplier<PrinterJob> printerJobs) {
        this.paperWidthPoints = paperWidthPoints;
        this.printerJobs = printerJobs;
    }

    public String imprimir(String pdfUrl, String numero) throws IOException, InterruptedException, PrinterException {
        PrinterJob job = printerJobs.get();
        var service = job.getPrintService();
        if (service == null) {
            throw new PrinterException("No hay una impresora predeterminada disponible. Configúrala en el sistema.");
        }
        Path temporal = Files.createTempFile("factura-dataico-", ".pdf");
        try {
            new DataicoPdfDownloader().descargar(pdfUrl, temporal);
            try (var document = Loader.loadPDF(temporal.toFile())) {
                if (!document.getCurrentAccessPermission().canPrint()) {
                    throw new PrinterException("El PDF de Dataico no permite impresión.");
                }
                if (document.getNumberOfPages() == 0) {
                    throw new IOException("El PDF de Dataico no contiene páginas para imprimir.");
                }
                job.setJobName("Factura " + (numero == null || numero.isBlank() ? "Dataico" : numero));
                Book pages = new Book();
                PDFPrintable printable = new PDFPrintable(document, Scaling.ACTUAL_SIZE, false, 0, false);
                for (var page : document.getPages()) {
                    PageFormat format = job.defaultPage();
                    Paper paper = new Paper();
                    boolean rotated = Math.floorMod(page.getRotation(), 180) == 90;
                    double height = rotated ? page.getCropBox().getWidth() : page.getCropBox().getHeight();
                    paper.setSize(paperWidthPoints, height);
                    paper.setImageableArea(0, 0, paperWidthPoints, height);
                    format.setPaper(paper);
                    // Mismo ancho térmico del POS, sin escala ni centrado artificial.
                    pages.append(printable, job.validatePage(format));
                }
                job.setPageable(pages);
                job.print();
                return service.getName();
            }
        } finally {
            try {
                Files.deleteIfExists(temporal);
            } catch (IOException exception) {
                // La limpieza no debe reportar una impresión exitosa como fallida y provocar duplicados.
                temporal.toFile().deleteOnExit();
            }
        }
    }
}
