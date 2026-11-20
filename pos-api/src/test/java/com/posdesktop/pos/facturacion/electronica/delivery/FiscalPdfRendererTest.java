package com.posdesktop.pos.facturacion.electronica.delivery;
import static org.junit.jupiter.api.Assertions.*;
import com.google.zxing.*;
import com.google.zxing.common.HybridBinarizer;
import java.nio.file.*;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class FiscalPdfRendererTest {
 static FiscalPrintDocument fixture(int count){return new FiscalPrintDocument("Invoice","PRUEBA123","a".repeat(96),"2026-11-19 12:00:00-05:00","EL MILENASO - FABIOLA VANEGAS","22058065","Carrera 13 # 10-17, Medellin, Antioquia, CO","Luis Carlos Rendón Cardona","1027891441","Calle de prueba 123, Medellin, Antioquia, CO","COP","18760000001","","https://catalogo-vpfe-hab.dian.gov.co/document/searchqr?documentkey="+"a".repeat(96),java.util.stream.IntStream.range(0,count).mapToObj(i->new FiscalPrintDocument.Line("Articulo "+(i+1)+" con descripcion larga, tamaño especial y caracteres ñ - "+"detalle ".repeat(8),"2","94","10000.00","20000.00","IVA 19%: 3800.00")).toList(),List.of("Subtotal: 20000.00","IVA: 3800.00","TOTAL COP: 23800.00"),List.of("Forma de pago: Contado","Autorizacion: 2026-01-01 a 2026-12-31","Prefijo: PRUEBA | Rango: 1 a 1000000"));}
 @Test void producesReadableActualSizePdfsWithDecodableQrAtAllWidths()throws Exception{
  var renderer=new FiscalPdfRenderer();Path output=Path.of("target/pdf-review");Files.createDirectories(output);
  for(var paper:FiscalPdfRenderer.Paper.values()){byte[] bytes=renderer.render(fixture(2),paper);Files.write(output.resolve("PRUEBAS-"+paper+".pdf"),bytes);try(var pdf=Loader.loadPDF(bytes)){String text=new PDFTextStripper().getText(pdf);assertTrue(text.contains("SIN VALIDEZ FISCAL"));assertTrue(text.contains("23800.00"));assertTrue(text.contains("22058065"));float expected=paper==FiscalPdfRenderer.Paper.A4?595.28f:paper==FiscalPdfRenderer.Paper.MM80?80*72f/25.4f:58*72f/25.4f;assertEquals(expected,pdf.getPage(0).getMediaBox().getWidth(),.1);var image=new PDFRenderer(pdf).renderImageWithDPI(pdf.getNumberOfPages()-1,180);int[] pixels=image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth());javax.imageio.ImageIO.write(image,"png",output.resolve("PRUEBAS-"+paper+".png").toFile());var result=new MultiFormatReader().decode(new BinaryBitmap(new HybridBinarizer(new RGBLuminanceSource(image.getWidth(),image.getHeight(),pixels))),Map.of(DecodeHintType.TRY_HARDER,true,DecodeHintType.POSSIBLE_FORMATS,List.of(BarcodeFormat.QR_CODE)));assertEquals(fixture(2).qr(),result.getText());javax.imageio.ImageIO.write(image,"png",output.resolve("PRUEBAS-"+paper+".png").toFile());}}
 }
 @Test void longInvoicesPaginateWithoutDroppingLines()throws Exception{for(var paper:FiscalPdfRenderer.Paper.values())try(var pdf=Loader.loadPDF(new FiscalPdfRenderer().render(fixture(120),paper))){assertTrue(pdf.getNumberOfPages()>1);String text=new PDFTextStripper().getText(pdf);assertTrue(text.contains("120."));assertTrue(text.contains("23800.00"));}}
}
