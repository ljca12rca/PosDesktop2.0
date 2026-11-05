package com.posdesktop.pos.facturacion.electronica.fiscal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.posdesktop.pos.modelo.relacional.*;
import com.posdesktop.pos.repositorio.relacional.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

class FiscalSignatureServiceTest {
    final FiscalDocumentRepository documents=mock(FiscalDocumentRepository.class);
    final FiscalEventRepository events=mock(FiscalEventRepository.class);
    final DianSettingsRepository settings=mock(DianSettingsRepository.class);
    final FiscalSecretVault vault=mock(FiscalSecretVault.class);
    final FiscalJson json=new FiscalJson();
    final FiscalSignatureService service=new FiscalSignatureService(documents,events,settings,vault,json);
    FiscalDocumentRecord source(String provider,String environment,String format,String hash){
        String xml="<Invoice/>";
        var doc=new FiscalDocumentRecord(UUID.randomUUID(),UUID.randomUUID(),"BORRADOR",provider,environment,"test","TEST1",format,"{}",xml,
                hash==null?json.hash("{}\n"+xml):hash,"Cliente",BigDecimal.ONE);
        when(documents.findById(doc.getId())).thenReturn(Optional.of(doc));return doc;
    }
    @Test void blocksProductionBeforeReadingCertificate(){
        var doc=source("DIAN_DIRECT","PRODUCCION","DIAN_INVOICE_V1",null);
        assertThrows(IllegalArgumentException.class,()->service.sign(doc.getId()));verifyNoInteractions(settings,vault,events);
    }
    @Test void blocksDataicoAndAlreadySignedBeforeReadingCertificate(){
        var doc=source("DATAICO","PRUEBAS","DIAN_INVOICE_V1",null);
        assertThrows(IllegalArgumentException.class,()->service.sign(doc.getId()));
        var signed=source("DIAN_DIRECT","PRUEBAS",FiscalSignatureService.FORMAT,null);
        assertThrows(IllegalArgumentException.class,()->service.sign(signed.getId()));verifyNoInteractions(settings,vault,events);
    }
    @Test void refusesCorruptedSourceEvidence(){
        var doc=source("DIAN_DIRECT","PRUEBAS","DIAN_INVOICE_V1","wrong");
        assertThrows(IllegalArgumentException.class,()->service.sign(doc.getId()));verifyNoInteractions(settings,vault,events);
    }
    @Test void missingCertificateNeverCreatesSignedEvidence(){
        var doc=source("DIAN_DIRECT","PRUEBAS","DIAN_INVOICE_V1",null);var row=mock(DianSettings.class);
        when(settings.lockSettings("PRUEBAS")).thenReturn(Optional.of(row));
        when(vault.decrypt(null,"PRUEBAS")).thenReturn(new FiscalSecretVault.Secrets("PRUEBAS",null,null,null,null));
        assertThrows(IllegalArgumentException.class,()->service.sign(doc.getId()));verify(documents,never()).saveAndFlush(any());verifyNoInteractions(events);
    }
    @Test void validationRefusesCorruptStorageAndDoesNotNeedSecrets(){
        var doc=source("DIAN_DIRECT","PRUEBAS",FiscalSignatureService.FORMAT,"wrong");
        assertThrows(IllegalArgumentException.class,()->service.verify(doc.getId()));verifyNoInteractions(settings,vault,events);
    }
}
