package com.posdesktop.pos.integration;
import java.awt.print.*;
import java.nio.file.Path;
import javax.print.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.*;

/** Imprime el PDF propio respetando sus dimensiones fisicas, sin escalar ni abrir visor. */
public final class FiscalPdfPrinter {
 public String print(Path file,String number)throws Exception{
  var printer=DataicoPdfPrinter.selectPrinter(PrintServiceLookup.lookupPrintServices(null,null),PrintServiceLookup.lookupDefaultPrintService());
  try(var pdf=Loader.loadPDF(file.toFile())){if(!pdf.getCurrentAccessPermission().canPrint()||pdf.getNumberOfPages()==0)throw new PrinterException("El PDF no permite impresion.");var job=PrinterJob.getPrinterJob();job.setPrintService(printer);job.setJobName("POSDesktop PRUEBAS "+number);job.setCopies(1);job.setPageable(pages(pdf,job::validatePage));job.print();return printer.getName();}
 }
 static Book pages(PDDocument document,java.util.function.UnaryOperator<PageFormat> validate){var pages=new Book();var printable=new PDFPrintable(document,Scaling.ACTUAL_SIZE,false,0,false);for(var page:document.getPages()){var box=page.getCropBox();Paper paper=new Paper();paper.setSize(box.getWidth(),box.getHeight());paper.setImageableArea(0,0,paper.getWidth(),paper.getHeight());var format=new PageFormat();format.setPaper(paper);double expectedWidth=format.getWidth(),expectedHeight=format.getHeight();var actual=validate.apply((PageFormat)format.clone());if(Math.abs(actual.getWidth()-expectedWidth)>5||actual.getHeight()+5<expectedHeight)throw new IllegalArgumentException("El controlador cambio el ancho o largo del papel. Revisa el formato A4, 80 mm o 58 mm y la longitud admitida.");pages.append(printable,actual);}return pages;}
}
