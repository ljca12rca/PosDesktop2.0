package com.posdesktop.pos.integration;

import com.posdesktop.pos.printing.ReceiptPaper;
import java.awt.print.Book;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.function.BooleanSupplier;
import java.util.concurrent.CancellationException;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.standard.PrinterIsAcceptingJobs;
import javax.print.attribute.standard.PrinterState;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.PDFPrintable;
import org.apache.pdfbox.printing.Scaling;

/** Imprime el PDF original sin visor, dialogos ni ajustes de escala. */
public final class DataicoPdfPrinter {
    @FunctionalInterface
    interface PrintAction {
        void print(Path pdf, PrintService printer, String jobName, BooleanSupplier allowed) throws IOException, PrinterException;
    }

    private final Supplier<PrintService[]> printers;
    private final Supplier<PrintService> defaultPrinter;
    private final PrintAction printAction;

    public DataicoPdfPrinter() {
        this(() -> PrintServiceLookup.lookupPrintServices(null, null),
                PrintServiceLookup::lookupDefaultPrintService, DataicoPdfPrinter::printPdf);
    }

    DataicoPdfPrinter(Supplier<PrintService[]> printers, Supplier<PrintService> defaultPrinter, PrintAction printAction) {
        this.printers = printers;
        this.defaultPrinter = defaultPrinter;
        this.printAction = printAction;
    }

    public String imprimir(String url, String numero) throws IOException, InterruptedException, PrinterException {
        return imprimir(url, numero, () -> true);
    }

    public String imprimir(String url, String numero, BooleanSupplier allowed) throws IOException, InterruptedException, PrinterException {
        checkAllowed(allowed);
        PrintService printer = selectPrinter(printers.get(), defaultPrinter.get());
        Path directory = Files.createTempDirectory("pos-dataico-print-");
        Path pdf = directory.resolve("factura.pdf");
        try {
            new DataicoPdfDownloader().descargar(url, pdf);
            checkAllowed(allowed);
            printAction.print(pdf, printer, "Factura Dataico " + (numero == null ? "" : numero), allowed);
            return printer.getName(); // Aceptado por la cola; no confirma salida fisica del papel.
        } finally {
            try {
                Files.deleteIfExists(pdf);
                Files.deleteIfExists(directory);
            } catch (IOException cleanupError) {
                // No informar fallo de impresion tras enviar: podria causar una reimpresion duplicada.
                directory.toFile().deleteOnExit();
                pdf.toFile().deleteOnExit();
            }
        }
    }

    static PrintService selectPrinter(PrintService[] services, PrintService preferred) throws PrinterException {
        var available = Arrays.stream(services).filter(DataicoPdfPrinter::isAvailable).toList();
        if (preferred != null) {
            for (PrintService printer : available) {
                if (printer.getName().equals(preferred.getName())) return printer;
            }
        }
        if (available.size() == 1) return available.getFirst();
        throw new PrinterException(available.isEmpty()
                ? "No hay una impresora disponible. Revisa la conexion y la cola de Windows."
                : "Hay varias impresoras. Define una predeterminada en Windows.");
    }

    private static boolean isAvailable(PrintService service) {
        String name = service.getName().toLowerCase(Locale.ROOT);
        // Los controladores virtuales suelen abrir un dialogo Guardar, incompatible con este flujo.
        return !name.contains("pdf") && !name.contains("xps") && !name.contains("onenote") && !name.equals("fax")
                && !PrinterIsAcceptingJobs.NOT_ACCEPTING_JOBS.equals(service.getAttribute(PrinterIsAcceptingJobs.class))
                && !PrinterState.STOPPED.equals(service.getAttribute(PrinterState.class));
    }

    static PDFPrintable actualSizePrintable(PDDocument document) {
        return new PDFPrintable(document, Scaling.ACTUAL_SIZE, false, 0, false);
    }

    static Book actualSizePages(PDDocument document, java.util.function.UnaryOperator<PageFormat> validate) {
        Book pages = new Book();
        PDFPrintable printable = actualSizePrintable(document);
        for (var page : document.getPages()) {
            var crop = page.getCropBox();
            boolean rotated = Math.floorMod(page.getRotation(), 180) != 0;
            double height = rotated ? crop.getWidth() : crop.getHeight();
            Paper paper = new Paper();
            height = Math.max(ReceiptPaper.MIN_HEIGHT_POINTS, height);
            paper.setSize(ReceiptPaper.WIDTH_POINTS, height);
            paper.setImageableArea(0, 0, paper.getWidth(), paper.getHeight());
            PageFormat format = new PageFormat();
            format.setOrientation(PageFormat.PORTRAIT);
            format.setPaper(paper);
            // Sin margen artificial de una pulgada. Solo limitaciones fisicas del controlador.
            pages.append(printable, validate.apply(format));
        }
        return pages;
    }

    private static void checkAllowed(BooleanSupplier allowed) {
        if (!allowed.getAsBoolean()) throw new CancellationException("Impresion cancelada por el usuario.");
    }

    private static void printPdf(Path pdf, PrintService printer, String jobName, BooleanSupplier allowed) throws IOException, PrinterException {
        try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
            if (!document.getCurrentAccessPermission().canPrint()) {
                throw new PrinterException("El PDF no permite impresion.");
            }
            if (document.getNumberOfPages() == 0) throw new PrinterException("El PDF no contiene paginas.");
            PrinterJob job = PrinterJob.getPrinterJob();
            job.setPrintService(printer);
            job.setJobName(jobName);
            job.setCopies(1);
            // Mismo ancho termico del POS normal; largo independiente para cada pagina del PDF.
            job.setPageable(actualSizePages(document, job::validatePage));
            checkAllowed(allowed);
            job.print();
        }
    }
}
