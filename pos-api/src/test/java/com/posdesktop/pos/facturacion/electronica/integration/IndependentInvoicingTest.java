package com.posdesktop.pos.facturacion.electronica.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.posdesktop.pos.facturacion.electronica.application.*;
import com.posdesktop.pos.facturacion.electronica.config.IndependentInvoicingConfig;
import com.posdesktop.pos.facturacion.electronica.domain.DocumentoElectronico;
import com.posdesktop.pos.invoicing.application.*;
import com.posdesktop.pos.invoicing.domain.*;
import com.posdesktop.pos.modelo.relacional.DetalleVenta;
import com.posdesktop.pos.modelo.relacional.Venta;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockPropertySource;
import org.springframework.core.env.StandardEnvironment;

class IndependentInvoicingTest {
    @Test void dataicoPermanecePredeterminadoYUsaElContratoAnteriorSinCambiarDocumento() {
        var provider = mock(ProveedorFacturacionElectronica.class);
        when(provider.codigoProveedor()).thenReturn("dataico");
        var document = mock(DocumentoElectronico.class);
        new DataicoLegacyAdapter(new FacturacionElectronicaService(List.of(provider))).emitExisting(document);
        verify(provider).codigoProveedor();
        verify(provider).emitir(same(document));
        verifyNoMoreInteractions(provider);
    }
    @Test void seleccionDirectaRechazaEmisionAntesDeContactarDataico() {
        var provider = mock(ProveedorFacturacionElectronica.class);
        var service = new FacturacionElectronicaService(List.of(provider));
        assertThrows(UnsupportedOperationException.class, () -> service.emitir("DIAN_DIRECT", mock(DocumentoElectronico.class)));
        verifyNoInteractions(provider);
    }
    @Test void configuracionPredeterminadaSinCredenciales() {
        var repository=mock(com.posdesktop.pos.repositorio.relacional.FiscalProviderSettingsRepository.class);
        when(repository.findById(1)).thenReturn(java.util.Optional.of(new com.posdesktop.pos.modelo.relacional.FiscalProviderSettings()));
        var service=new com.posdesktop.pos.facturacion.electronica.fiscal.FiscalSettingsService(repository,null,null,null);
        assertEquals("DATAICO",service.provider().provider());
        assertFalse(service.provider().directEmissionEnabled());
    }
    @Test void snapshotVentaSinRepositoriosSinMutarOriginalYConFiscalidadExplicita() {
        var detail = mock(DetalleVenta.class);
        when(detail.getId()).thenReturn(UUID.fromString("22222222-2222-4222-8222-222222222222"));
        when(detail.getOrden()).thenReturn(1);
        when(detail.getDescripcion()).thenReturn("Articulo original");
        when(detail.getCantidad()).thenReturn(BigDecimal.ONE);
        when(detail.getPrecioUnitario()).thenReturn(new BigDecimal("100"));
        when(detail.getDescuento()).thenReturn(BigDecimal.ZERO);
        when(detail.getImpuesto()).thenReturn(BigDecimal.ZERO);
        when(detail.getTotal()).thenReturn(new BigDecimal("100"));
        var sale = mock(Venta.class);
        when(sale.getId()).thenReturn(UUID.fromString("11111111-1111-4111-8111-111111111111"));
        when(sale.getFechaVenta()).thenReturn(LocalDateTime.of(2026,10,29,10,0));
        when(sale.getDetalles()).thenReturn(List.of(detail));
        when(sale.getDescuentoTotal()).thenReturn(BigDecimal.ZERO);
        when(sale.getImpuestoTotal()).thenReturn(BigDecimal.ZERO);
        when(sale.getTotal()).thenReturn(new BigDecimal("100"));
        var party = new FiscalInvoice.Party("31","900000001","1","PRUEBA","R-99-PN","01","IVA",
                new FiscalInvoice.Address("CALLE PRUEBA", "05001", "Medellin", "05", "Antioquia", "CO"));
        var numbering = new FiscalInvoice.Numbering("PRUEBA", "EJEMPLO", LocalDate.of(2026,1,1), LocalDate.of(2026,12,31),1,100);
        var payment = new FiscalInvoice.Payment("10",LocalDate.of(2026,10,29),"pago");
        var mapper = new SaleFiscalSnapshotMapper();
        var classification = new SaleFiscalSnapshotMapper.Classification("94",
                List.of(new FiscalInvoice.Tax(FiscalInvoice.Treatment.EXCLUDED,"01","IVA",BigDecimal.ZERO)));
        var context = new SaleFiscalSnapshotMapper.Context("PRUEBA1",party,party,numbering,payment,Map.of(1,classification));
        var snapshot = mapper.snapshot(sale,context);
        when(detail.getDescripcion()).thenReturn("Modificacion posterior");
        assertEquals("Articulo original", snapshot.lines().getFirst().description());
        assertEquals("-05:00", snapshot.issuedAt().getOffset().toString());
        when(sale.getDescuentoTotal()).thenReturn(new BigDecimal("10"));
        when(sale.getTotal()).thenReturn(new BigDecimal("90"));
        var adjustedContext = new SaleFiscalSnapshotMapper.Context("PRUEBA1",party,party,numbering,payment,Map.of(1,classification),
                List.of(new FiscalInvoice.Adjustment(false,"00","Descuento financiero",new BigDecimal("10"))),
                List.of(new FiscalInvoice.Withholding("06","ReteRenta",new BigDecimal("100"),new BigDecimal("2.5"))));
        var adjusted = mapper.snapshot(sale,adjustedContext);
        assertEquals(new BigDecimal("90.00"), adjusted.declaredPayable());
        assertEquals(new BigDecimal("2.50"), new InvoiceCalculator().calculate(adjusted).withholding());
        assertThrows(InvoiceValidationException.class, () -> mapper.snapshot(sale,context));
        assertThrows(InvoiceValidationException.class, () -> mapper.snapshot(sale,
                new SaleFiscalSnapshotMapper.Context("PRUEBA1",party,party,numbering,payment,Map.of())));
        verify(sale, never()).recalcularTotales();
    }
}
