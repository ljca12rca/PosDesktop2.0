package com.posdesktop.pos.facturacion.electronica.delivery;

import com.google.zxing.*;
import com.google.zxing.qrcode.QRCodeWriter;
import java.io.*;
import java.util.*;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.springframework.stereotype.Component;

/** PDF propio en A4 o papel termico, con medidas fisicas y salto de pagina sin reducir escala. */
@Component
public final class FiscalPdfRenderer {
    public enum Paper{A4,MM80,MM58}
    private record Text(String value,boolean strong){}
    public byte[] render(FiscalPrintDocument invoice,Paper paper){
        try(var doc=new PDDocument();var output=new ByteArrayOutputStream()){
            float width=switch(paper){case A4->PDRectangle.A4.getWidth();case MM80->80*72f/25.4f;case MM58->58*72f/25.4f;};
            float margin=paper==Paper.A4?38:10,size=paper==Paper.A4?10:paper==Paper.MM80?8:7,leading=size*1.45f,usable=width-2*margin;
            var normal=new PDType1Font(Standard14Fonts.FontName.HELVETICA);var bold=new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            List<Text> rows=new ArrayList<>();add(rows,"PRUEBAS - SIN VALIDEZ FISCAL",true);add(rows,invoice.issuer(),true);add(rows,"NIT / documento: "+invoice.issuerId(),false);add(rows,invoice.issuerAddress(),false);add(rows,"",false);
            String kind=switch(invoice.kind()){case "Invoice"->"Factura electronica de venta";case "CreditNote"->"Devolucion / Anulacion - Nota credito";default->"Ajuste de valor - Nota debito";};
            add(rows,kind+" "+invoice.number(),true);add(rows,"Generacion: "+invoice.issued(),false);if(!invoice.resolution().isBlank())add(rows,"Resolucion: "+invoice.resolution(),false);if(!invoice.reference().isBlank())add(rows,"Factura de referencia: "+invoice.reference(),true);
            add(rows,"Cliente: "+invoice.customer(),true);add(rows,"Identificacion: "+invoice.customerId(),false);add(rows,invoice.customerAddress(),false);add(rows,"",false);
            invoice.metadata().forEach(text->add(rows,text,false));add(rows,"",false);
            int order=0;for(var line:invoice.lines()){add(rows,(++order)+". "+line.description(),true);add(rows,line.quantity()+" x "+line.unitPrice()+" "+invoice.currency()+" | Unidad: "+line.unit(),false);add(rows,"Base: "+line.net()+" | "+line.taxes(),false);add(rows,"",false);}
            for(String total:invoice.totals())add(rows,total,total.startsWith("TOTAL"));add(rows,"",false);add(rows,("Invoice".equals(invoice.kind())?"CUFE: ":"CUDE: ")+invoice.key(),false);add(rows,"Consulta DIAN en ambiente de habilitacion (QR).",false);add(rows,"Documento de prueba. No acredita expedicion ni aceptacion DIAN.",true);
            List<Text> wrapped=new ArrayList<>();for(var row:rows)for(String text:wrap(row.value(),row.strong()?bold:normal,size,usable))wrapped.add(new Text(text,row.strong()));
            float qrSize=paper==Paper.A4?112:Math.min(112,usable),footer=qrSize+margin+leading*2;
            float maxHeight=paper==Paper.A4?PDRectangle.A4.getHeight():1100;
            int maxRows=(int)((maxHeight-2*margin-leading*2)/leading);int cursor=0;
            while(cursor<wrapped.size()){
                int remaining=wrapped.size()-cursor;int count=Math.min(maxRows,remaining);boolean last=count==remaining;
                if(last&&count*leading+footer+2*margin>maxHeight){count=Math.max(1,count-(int)Math.ceil(footer/leading));last=false;}
                float height=paper==Paper.A4?maxHeight:Math.max(180,count*leading+2*margin+(last?footer:leading*2));
                var page=new PDPage(new PDRectangle(width,height));doc.addPage(page);
                try(var canvas=new PDPageContentStream(doc,page)){
                    float y=height-margin-size;
                    for(int i=0;i<count;i++){var row=wrapped.get(cursor++);canvas.beginText();canvas.setFont(row.strong()?bold:normal,size);canvas.setNonStrokingColor(row.strong()?new java.awt.Color(25,80,71):java.awt.Color.BLACK);canvas.newLineAtOffset(margin,y);canvas.showText(clean(row.value()));canvas.endText();y-=leading;}
                    if(last){var matrix=new QRCodeWriter().encode(invoice.qr(),BarcodeFormat.QR_CODE,360,360,Map.of(EncodeHintType.MARGIN,4,EncodeHintType.ERROR_CORRECTION,com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M));var image=new java.awt.image.BufferedImage(matrix.getWidth(),matrix.getHeight(),java.awt.image.BufferedImage.TYPE_INT_RGB);for(int x=0;x<matrix.getWidth();x++)for(int yy=0;yy<matrix.getHeight();yy++)image.setRGB(x,yy,matrix.get(x,yy)?0x000000:0xffffff);canvas.drawImage(LosslessFactory.createFromImage(doc,image),(width-qrSize)/2,margin+leading,qrSize,qrSize);}
                    canvas.beginText();canvas.setFont(normal,Math.max(6,size-2));canvas.setNonStrokingColor(java.awt.Color.DARK_GRAY);canvas.newLineAtOffset(margin,margin/2);canvas.showText("POSDesktop | PRUEBAS | "+doc.getNumberOfPages());canvas.endText();
                }
            }
            doc.getDocumentInformation().setTitle("PRUEBAS - "+invoice.number());doc.getDocumentInformation().setProducer("POSDesktop");doc.save(output);return output.toByteArray();
        }catch(Exception e){throw new IllegalStateException("No fue posible generar el PDF fiscal de prueba.",e);}
    }
    private static void add(List<Text> rows,String text,boolean bold){rows.add(new Text(text,bold));}
    private static List<String> wrap(String text,PDFont font,float size,float width)throws IOException{List<String> lines=new ArrayList<>();StringBuilder line=new StringBuilder();for(char c:clean(text).toCharArray()){String next=line.toString()+c;if(font.getStringWidth(next)/1000*size>width&&line.length()>0){int space=line.lastIndexOf(" ");if(space>0){lines.add(line.substring(0,space));String carry=line.substring(space+1);line.setLength(0);line.append(carry);}else{lines.add(line.toString());line.setLength(0);}}if(!(c==' '&&line.isEmpty()))line.append(c);}lines.add(line.toString());return lines;}
    private static String clean(String text){StringBuilder result=new StringBuilder();for(char c:Objects.toString(text,"").toCharArray())result.append((c>=32&&c<127)||(c>=160&&c<=255)?c:' ');return result.toString();}
}
