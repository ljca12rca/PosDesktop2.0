package com.posdesktop.pos.facturacion.electronica.adjustments;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.posdesktop.pos.facturacion.electronica.fiscal.*;
import com.posdesktop.pos.modelo.relacional.*;
import com.posdesktop.pos.repositorio.relacional.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AdjustmentEvidenceTest {
    @Test void sentAdjustmentKeepsItsStateInFiscalEvidenceWithoutOneLookupPerRow(){
        var documents=mock(FiscalDocumentRepository.class);var adjustments=mock(FiscalAdjustmentRepository.class);
        var controller=new FiscalDocumentsController(mock(FiscalDraftService.class),documents,mock(FiscalOperationRepository.class),mock(FiscalEventRepository.class),mock(com.posdesktop.pos.ventas.service.VentasService.class),mock(VentaRepositorio.class),mock(FiscalSignatureService.class),adjustments);
        var doc=new FiscalDocumentRecord(UUID.randomUUID(),null,"BORRADOR","DATAICO","PRUEBAS","account","NC1","DATAICO_ADJUSTMENT_V1","{}",null,"a".repeat(64),"Cliente",BigDecimal.TEN);
        var note=mock(FiscalAdjustmentRecord.class);when(note.getDocumentId()).thenReturn(doc.getId());when(note.getState()).thenReturn("RECIBIDO");when(documents.findTop200ByOrderByCreadoEnDesc()).thenReturn(List.of(doc));when(adjustments.findByDocumentIdIn(List.of(doc.getId()))).thenReturn(List.of(note));
        assertEquals("RECIBIDO",controller.documents(new MockHttpServletRequest()).data().getFirst().state());verify(adjustments,never()).findByDocumentId(any());
    }
}
