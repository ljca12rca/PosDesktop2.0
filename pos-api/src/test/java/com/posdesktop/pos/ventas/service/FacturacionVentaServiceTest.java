package com.posdesktop.pos.ventas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.posdesktop.pos.facturacion.electronica.application.FacturacionElectronicaService;
import com.posdesktop.pos.facturacion.electronica.config.DataicoProperties;
import com.posdesktop.pos.facturacion.electronica.config.DataicoConfiguracionService;
import com.posdesktop.pos.facturacion.electronica.domain.DocumentoElectronico;
import com.posdesktop.pos.facturacion.electronica.domain.ResultadoEmisionDocumento;
import com.posdesktop.pos.facturacion.electronica.infraestructura.dataico.DataicoApiException;
import com.posdesktop.pos.facturacion.electronica.infraestructura.dataico.DataicoInvoiceMapper;
import com.posdesktop.pos.facturacion.electronica.fiscal.*;
import com.posdesktop.pos.repositorio.relacional.*;
import com.posdesktop.pos.modelo.relacional.*;
import com.posdesktop.pos.modelo.relacional.ClienteElectronico;
import com.posdesktop.pos.modelo.relacional.DataicoConsecutivo;
import com.posdesktop.pos.modelo.relacional.DetalleVenta;
import com.posdesktop.pos.modelo.relacional.FacturaElectronica;
import com.posdesktop.pos.modelo.relacional.Venta;
import com.posdesktop.pos.repositorio.relacional.ClienteElectronicoRepositorio;
import com.posdesktop.pos.repositorio.relacional.DataicoConsecutivoRepositorio;
import com.posdesktop.pos.repositorio.relacional.FacturaElectronicaRepositorio;
import com.posdesktop.pos.repositorio.relacional.VentaRepositorio;
import java.time.Duration;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FacturacionVentaServiceTest {
    private final VentaRepositorio ventas = mock(VentaRepositorio.class);
    private final ClienteElectronicoRepositorio clientes = mock(ClienteElectronicoRepositorio.class);
    private final FacturacionElectronicaService dataico = mock(FacturacionElectronicaService.class);
    private final FacturaElectronicaRepositorio facturas = mock(FacturaElectronicaRepositorio.class);
    private final DataicoConsecutivoRepositorio consecutivos = mock(DataicoConsecutivoRepositorio.class);
    private final DataicoConfiguracionService configuracion = mock(DataicoConfiguracionService.class);
    private final DataicoProperties properties = new DataicoProperties(true, null, null, null, "token-ficticio-test",
            "FEE", "18760000001", "1789500096", true, "PRODUCCION",
            Duration.ofSeconds(5), Duration.ofSeconds(30));
    private final FiscalProviderSettingsRepository providers = mock(FiscalProviderSettingsRepository.class);
    private final FiscalDocumentRepository documents = mock(FiscalDocumentRepository.class);
    private final FiscalOperationRepository operations = mock(FiscalOperationRepository.class);
    private final FiscalEventRepository events = mock(FiscalEventRepository.class);
    private final FiscalEmissionTransactions transactions = new FiscalEmissionTransactions(ventas,clientes,facturas,consecutivos,configuracion,providers,documents,operations,events,new FiscalJson());
    private final FacturacionVentaService service = new FacturacionVentaService(transactions,new DataicoFiscalSender(dataico,configuracion));
    private final UUID ventaId = UUID.randomUUID();
    private final UUID clienteId = UUID.randomUUID();

    @Test
    void enviaLaConfiguracionGuardadaHastaElJsonYPersistenciaSinAlterarElPrefijoAnterior() {
        DataicoConsecutivo anterior = preparar(1789500100L);
        DataicoConsecutivo nuevo = new DataicoConsecutivo("ABC", 50L);
        when(consecutivos.findByPrefijoForUpdate("ABC")).thenReturn(Optional.of(nuevo));
        when(configuracion.paraEmision()).thenReturn(new DataicoProperties(true, null, null, null, "token-ficticio-test",
                "ABC", "987654", "1", false, "PRUEBAS", Duration.ofSeconds(5), Duration.ofSeconds(30)));
        cuandoDataicoAcepta("50");

        service.emitir(ventaId, clienteId);

        ArgumentCaptor<DocumentoElectronico> documento = ArgumentCaptor.forClass(DocumentoElectronico.class);
        verify(dataico).emitir(eq("dataico"), documento.capture());
        Map<?, ?> invoice = (Map<?, ?>) new DataicoInvoiceMapper(properties).toRequest(documento.getValue()).get("invoice");
        Map<?, ?> numbering = (Map<?, ?>) invoice.get("numbering");
        assertThat(invoice.get("env")).isEqualTo("PRUEBAS");
        assertThat(numbering.get("prefix")).isEqualTo("ABC");
        assertThat(numbering.get("resolution_number")).isEqualTo("987654");
        assertThat(numbering.get("flexible")).isEqualTo(false);
        assertThat(anterior.getSiguienteNumero()).isEqualTo(1789500100L);
        assertThat(nuevo.getSiguienteNumero()).isEqualTo(51L);
        ArgumentCaptor<FacturaElectronica> factura = ArgumentCaptor.forClass(FacturaElectronica.class);
        verify(facturas).saveAndFlush(factura.capture());
        assertThat(factura.getValue().getNumeracion()).isEqualTo("ABC");
        assertThat(factura.getValue().getNumeroFactura()).isEqualTo("ABC50");
    }

    @Test
    void devuelveElPdfOriginalDeDataicoYLoGuardaSinAlterarlo() {
        preparar(1789500096L);
        String pdfUrl = "https://documentos.example.test/original.pdf";
        when(dataico.emitir(eq("dataico"), any())).thenReturn(new ResultadoEmisionDocumento(
                "dataico", "id-prueba", "1789500096", "DIAN_RECHAZADO", null, null, pdfUrl, "Creada", null));

        var respuesta = service.emitir(ventaId, clienteId);

        assertThat(respuesta.pdfUrl()).isEqualTo(pdfUrl);
        ArgumentCaptor<FacturaElectronica> factura = ArgumentCaptor.forClass(FacturaElectronica.class);
        verify(facturas).saveAndFlush(factura.capture());
        assertThat(factura.getValue().getPdfUrl()).isEqualTo(pdfUrl);
        verify(dataico).emitir(eq("dataico"), any());
    }

    @Test
    void enviaElConsecutivoConfiguradoSinAplicarUnMaximoLocal() {
        DataicoConsecutivo consecutivo = preparar(1789500096L);
        cuandoDataicoAcepta("1789500096");

        service.emitir(ventaId, clienteId);

        ArgumentCaptor<DocumentoElectronico> documento = ArgumentCaptor.forClass(DocumentoElectronico.class);
        verify(dataico).emitir(eq("dataico"), documento.capture());
        Map<String, Object> payload = new DataicoInvoiceMapper(properties).toRequest(documento.getValue());
        Map<?, ?> factura = (Map<?, ?>) payload.get("invoice");
        assertThat(factura.get("number")).isEqualTo("1789500096");
        assertThat(factura.get("env")).isEqualTo("PRODUCCION");
        Map<?, ?> numeracion = (Map<?, ?>) factura.get("numbering");
        assertThat(numeracion.get("prefix")).isEqualTo("FEE");
        assertThat(numeracion.get("resolution_number")).isEqualTo("18760000001");
        assertThat(consecutivo.getSiguienteNumero()).isEqualTo(1789500097L);
    }

    @Test
    void reservaElConsecutivoAntesDeContactarAlProveedor() {
        DataicoConsecutivo consecutivo = preparar(994500001L);
        cuandoDataicoAcepta("994500001");

        service.emitir(ventaId, clienteId);

        ArgumentCaptor<DocumentoElectronico> documento = ArgumentCaptor.forClass(DocumentoElectronico.class);
        verify(dataico).emitir(eq("dataico"), documento.capture());
        assertThat(documento.getValue().numero()).isEqualTo("994500001");
        assertThat(consecutivo.getSiguienteNumero()).isEqualTo(994500002L);
        verify(facturas).saveAndFlush(any());
    }

    @Test
    void reintentaUnaVezConElNumeroSugeridoPorDataicoSinMaximoLocal() {
        DataicoConsecutivo consecutivo = preparar(994500001L);
        when(dataico.emitir(eq("dataico"), any())).thenThrow(new DataicoApiException(
                "El siguiente número 'FEE1789500095'", org.springframework.web.client.HttpServerErrorException.create(
                        org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,"Error",org.springframework.http.HttpHeaders.EMPTY,new byte[0],java.nio.charset.StandardCharsets.UTF_8)))
                .thenReturn(new ResultadoEmisionDocumento("dataico", "id-prueba", "1789500095",
                        "DIAN_NO_ENVIADO", null, null, null, "Creada", null));

        service.emitir(ventaId, clienteId);

        ArgumentCaptor<DocumentoElectronico> documentos = ArgumentCaptor.forClass(DocumentoElectronico.class);
        verify(dataico, times(2)).emitir(eq("dataico"), documentos.capture());
        assertThat(documentos.getAllValues()).extracting(DocumentoElectronico::numero)
                .containsExactly("994500001", "1789500095");
        assertThat(consecutivo.getSiguienteNumero()).isEqualTo(1789500096L);
    }

    @Test
    void propagaResultadoInciertoPeroConservaLaReservaParaNoDuplicar() {
        DataicoConsecutivo consecutivo = preparar(1789500095L);
        DataicoApiException rechazo = new DataicoApiException("Dataico respondió 500: rechazo de resolución", new RuntimeException());
        when(dataico.emitir(eq("dataico"), any())).thenThrow(rechazo);

        assertThatThrownBy(() -> service.emitir(ventaId, clienteId))
                .isSameAs(rechazo);

        verify(dataico).emitir(eq("dataico"), any());
        verify(facturas, never()).saveAndFlush(any());
        assertThat(consecutivo.getSiguienteNumero()).isEqualTo(1789500096L);
    }

    @Test
    void permiteElPrimerNumeroYRechazaCero() {
        properties.validarConsecutivo(1L);
        properties.validarConsecutivo(1789500095L);
        assertThatThrownBy(() -> properties.validarConsecutivo(0L))
                .hasMessageContaining("número positivo");
    }

    @Test
    void solicitarCorreoNoLoMarcaComoEnviadoSinConfirmacionDeDataico() {
        preparar(1789500096L);
        clientes.findById(clienteId).orElseThrow().setEmail("cliente@example.test");
        cuandoDataicoAcepta("1789500096");

        service.emitir(ventaId, clienteId);

        ArgumentCaptor<FacturaElectronica> factura = ArgumentCaptor.forClass(FacturaElectronica.class);
        verify(facturas).saveAndFlush(factura.capture());
        assertThat(factura.getValue().getEstadoCorreo()).isEqualTo("PENDIENTE");
    }

    @Test
    void soloGuardaCorreoEnviadoCuandoDataicoLoConfirma() {
        preparar(1789500096L);
        clientes.findById(clienteId).orElseThrow().setEmail("cliente@example.test");
        when(dataico.emitir(eq("dataico"), any())).thenReturn(new ResultadoEmisionDocumento(
                "dataico", "id-prueba", "1789500096", "DIAN_ACEPTADO", null, null, null, "Creada", "CORREO_ENVIADO"));

        service.emitir(ventaId, clienteId);

        ArgumentCaptor<FacturaElectronica> factura = ArgumentCaptor.forClass(FacturaElectronica.class);
        verify(facturas).saveAndFlush(factura.capture());
        assertThat(factura.getValue().getEstadoCorreo()).isEqualTo("ENVIADO");
    }

    @Test
    void guardaElFalloDelCorreoAunqueLaFacturaEsteCreada() {
        preparar(1789500096L);
        clientes.findById(clienteId).orElseThrow().setEmail("cliente@example.test");
        when(dataico.emitir(eq("dataico"), any())).thenReturn(new ResultadoEmisionDocumento(
                "dataico", "id-prueba", "1789500096", "DIAN_ACEPTADO", null, null, null, "Creada", "CORREO_FALLO"));

        service.emitir(ventaId, clienteId);

        ArgumentCaptor<FacturaElectronica> factura = ArgumentCaptor.forClass(FacturaElectronica.class);
        verify(facturas).saveAndFlush(factura.capture());
        assertThat(factura.getValue().getEstadoCorreo()).isEqualTo("ERROR");
    }

    private DataicoConsecutivo preparar(long numero) {
        when(providers.lockSettings()).thenReturn(Optional.of(new FiscalProviderSettings()));
        Map<UUID,FiscalOperation> operationStore=new java.util.HashMap<>();
        when(operations.lockOperation(any())).thenAnswer(i->Optional.ofNullable(operationStore.get(i.getArgument(0))));
        when(operations.saveAndFlush(any())).thenAnswer(i->{FiscalOperation operation=i.getArgument(0);operationStore.put(operation.getVentaId(),operation);return operation;});
        when(documents.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));
        when(configuracion.paraEmision()).thenReturn(properties);
        ClienteElectronico cliente = new ClienteElectronico();
        org.springframework.test.util.ReflectionTestUtils.setField(cliente,"id",clienteId);
        cliente.setTipoPersona("NATURAL");
        cliente.setTipoIdentificacion("CC");
        cliente.setIdentificacion("123456789");
        cliente.setPrimerNombre("Cliente");
        cliente.setPrimerApellido("Prueba");
        cliente.setDireccion("Calle 1 # 2-3");
        cliente.setPaisCodigo("CO");
        cliente.setDepartamentoCodigoDane("05");
        cliente.setCiudadCodigoDane("001");
        Venta venta = new Venta();
        org.springframework.test.util.ReflectionTestUtils.setField(venta,"id",ventaId);
        venta.setNumeroVenta("VTA-PRUEBA");
        DetalleVenta detalle = new DetalleVenta();
        detalle.setDescripcion("Artículo de prueba");
        detalle.setCantidad(BigDecimal.ONE);
        detalle.setPrecioUnitario(BigDecimal.TEN);
        venta.getDetalles().add(detalle);
        DataicoConsecutivo consecutivo = new DataicoConsecutivo("FEE", numero);
        when(ventas.findById(ventaId)).thenReturn(Optional.of(venta));
        when(ventas.findForFiscalUpdate(ventaId)).thenReturn(Optional.of(venta));
        when(clientes.findById(clienteId)).thenReturn(Optional.of(cliente));
        when(consecutivos.findByPrefijoForUpdate("FEE")).thenReturn(Optional.of(consecutivo));
        return consecutivo;
    }

    private void cuandoDataicoAcepta(String numero) {
        when(dataico.emitir(eq("dataico"), any())).thenReturn(new ResultadoEmisionDocumento(
                "dataico", "id-prueba", numero, "DIAN_NO_ENVIADO", null, null, null, "Creada", null));
    }
}
