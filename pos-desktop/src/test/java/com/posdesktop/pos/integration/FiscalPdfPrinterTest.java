package com.posdesktop.pos.integration;
import static org.junit.jupiter.api.Assertions.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;
class FiscalPdfPrinterTest {
 @Test void preservesPdfPhysicalDimensionsInsteadOfForcingThermalWidth()throws Exception{try(var pdf=new PDDocument()){pdf.addPage(new PDPage(PDRectangle.A4));pdf.addPage(new PDPage(new PDRectangle(58*72f/25.4f,700)));var book=FiscalPdfPrinter.pages(pdf,f->f);assertEquals(595.28,book.getPageFormat(0).getWidth(),.1);assertEquals(58*72f/25.4f,book.getPageFormat(1).getWidth(),.1);assertEquals(0,book.getPageFormat(1).getImageableX(),.01);}}
 @Test void rejectsPrinterDriverSubstitutionThatWouldCutOffContent()throws Exception{try(var pdf=new PDDocument()){pdf.addPage(new PDPage(PDRectangle.A4));assertThrows(IllegalArgumentException.class,()->FiscalPdfPrinter.pages(pdf,f->{var paper=f.getPaper();paper.setSize(200,842);f.setPaper(paper);return f;}));}}
}
