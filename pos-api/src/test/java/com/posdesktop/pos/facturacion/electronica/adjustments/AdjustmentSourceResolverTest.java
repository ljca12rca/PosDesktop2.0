package com.posdesktop.pos.facturacion.electronica.adjustments;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.posdesktop.pos.facturacion.electronica.config.DataicoConfiguracionService;
import com.posdesktop.pos.facturacion.electronica.fiscal.FiscalJson;
import com.posdesktop.pos.modelo.relacional.FacturaElectronica;
import com.posdesktop.pos.repositorio.relacional.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;

class AdjustmentSourceResolverTest {
    final FacturaElectronicaRepositorio invoices=mock(FacturaElectronicaRepositorio.class);
    final FiscalDocumentRepository documents=mock(FiscalDocumentRepository.class);
    final DataicoConfiguracionService settings=mock(DataicoConfiguracionService.class);
    final AdjustmentGateway gateway=mock(AdjustmentGateway.class);
    final AdjustmentSourceResolver resolver=new AdjustmentSourceResolver(invoices,documents,settings,gateway,new FiscalJson());
    final UUID id=UUID.randomUUID();
    Map<String,Object> original(){
        var f=mock(FacturaElectronica.class);when(f.getProveedor()).thenReturn("DATAICO");when(f.getVentaId()).thenReturn(UUID.randomUUID());when(f.getIdentificadorDataico()).thenReturn("original");when(f.getNumeroFactura()).thenReturn("FE7");when(f.getFechaEmision()).thenReturn(LocalDateTime.of(2026,10,30,10,0));when(f.getClienteNombre()).thenReturn("Cliente");when(f.getTotal()).thenReturn(new BigDecimal("119.00"));when(invoices.findById(id)).thenReturn(Optional.of(f));
        when(settings.paraProveedor()).thenReturn(DataicoAdjustmentGatewayTest.properties(DataicoAdjustmentGatewayTest.ACCOUNT));
        var body=new LinkedHashMap<String,Object>();body.put("uuid","original");body.put("number","7");body.put("numbering",Map.of("prefix","FE"));body.put("dian_status","DIAN_ACEPTADO");body.put("env","PRODUCCION");
        body.put("items",List.of(Map.of("description","Original & ñ","sku","SKU","quantity",1,"price",100,"taxes",List.of(Map.of("tax_category","IVA","tax_rate",19)))));
        when(gateway.invoice("original")).thenReturn(body);return body;
    }
    @Test void usesRemoteOriginalDetailAndPreservesEnvironment(){original();var source=resolver.resolve("DATAICO",id);assertEquals("PRODUCCION",source.environment());assertEquals("Original & ñ",source.lines().getFirst().description());assertEquals(new BigDecimal("19"),source.lines().getFirst().taxes().getFirst().percent());assertEquals(64,source.hash().length());}
    @Test void blocksUnacceptedInvoicesAndWrongAccount(){var body=original();body.put("dian_status","DIAN_RECHAZADO");assertThrows(IllegalArgumentException.class,()->resolver.resolve("DATAICO",id));body.put("dian_status","DIAN_ACEPTADO");body.put("dataico_account_id","another-account");assertThrows(IllegalArgumentException.class,()->resolver.resolve("DATAICO",id));}
    @Test void refusesToInferMissingTaxesOrSilentlyChangeLocalTotals(){var body=original();body.put("items",List.of(Map.of("description","Original","sku","SKU","quantity",1,"price",100)));assertThrows(IllegalArgumentException.class,()->resolver.resolve("DATAICO",id));}
}
