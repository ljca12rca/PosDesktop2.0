package com.posdesktop.pos.invoicing.infrastructure.transport;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.zip.*;

/** Un documento firmado por ZIP: permite correlacion inequívoca, con nomenclatura 6.5.7–6.5.8. */
public final class DianZipPackage {
    public record Archive(String zipName,String xmlName,byte[] bytes){
        public Archive{bytes=bytes.clone();}
        @Override public byte[] bytes(){return bytes.clone();}
    }
    public Archive create(String signedXml,long fileSequence,int calendarYear){
        if(signedXml==null||signedXml.length()>5_000_000||fileSequence<1||fileSequence>0xffffffffL||calendarYear<2000||calendarYear>2099)throw new IllegalArgumentException("Tamaño, año o consecutivo de archivo invalido.");
        var identity=DianDocumentIdentity.read(signedXml);
        String suffix=String.format(java.util.Locale.ROOT,"%010d000%02d%08x",Long.parseLong(identity.issuerNit()),calendarYear%100,fileSequence);
        String xmlName=identity.filePrefix()+suffix+".xml",zipName="z"+suffix+".zip";
        try(var out=new ByteArrayOutputStream()){
            try(var zip=new ZipOutputStream(out,StandardCharsets.UTF_8)){
                zip.setLevel(6);var entry=new ZipEntry(xmlName);entry.setTimeLocal(LocalDateTime.of(1980,1,1,0,0));zip.putNextEntry(entry);zip.write(signedXml.getBytes(StandardCharsets.UTF_8));zip.closeEntry();
            }
            return new Archive(zipName,xmlName,out.toByteArray());
        }catch(IOException e){throw new IllegalStateException("No fue posible empaquetar el XML firmado.",e);}
    }
}
