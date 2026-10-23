package com.posdesktop.pos.ventas.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.posdesktop.pos.facturacion.electronica.application.FacturacionElectronicaService;
import com.posdesktop.pos.facturacion.electronica.domain.ResultadoEmisionDocumento;
import com.posdesktop.pos.facturacion.electronica.infraestructura.dataico.DataicoApiException;
import com.posdesktop.pos.modelo.relacional.FacturaElectronica;
import com.posdesktop.pos.repositorio.relacional.FacturaElectronicaRepositorio;
import com.posdesktop.pos.ventas.api.dto.EnviarCorreoFacturaRequest;
import jakarta.validation.Validation;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class FacturasElectronicasAccionesServiceTest {
    private final FacturaElectronicaRepositorio facturas = mock(FacturaElectronicaRepositorio.class);
    private final FacturacionElectronicaService dataico = mock(FacturacionElectronicaService.class);
    private final FacturasElectronicasAccionesService service = new FacturasElectronicasAccionesService(
            facturas, dataico, new FacturasElectronicasConsultaService(facturas));
    private final UUID id = UUID.randomUUID();
    private FacturaElectronica factura;

    @BeforeEach
    void preparar() {
        factura = new FacturaElectronica();
        ReflectionTestUtils.setField(factura, "id", id);
        factura.setIdentificadorDataico("uuid-dataico");
        factura.setNumeroFactura("FEE1");
        factura.setEstadoDian("DIAN_RECHAZADO");
        factura.setEstadoCorreo("NO_ENVIADO");
        factura.setClienteEmail("original@example.test");
        when(facturas.findById(id)).thenReturn(Optional.of(factura));
        when(facturas.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void enviaADianUsandoElUuidExistenteSinCrearOtraFactura() {
        when(dataico.consultarFactura("dataico", "FEE1")).thenReturn(estado("DIAN_ACEPTADO", "CORREO_NO_ENVIADO"));

        var resultado = service.enviarDian(id);

        verify(dataico).reenviar("dataico", "uuid-dataico", true, false, null);
        verify(dataico, never()).emitir(anyString(), any());
        assertThat(resultado.estadoDian()).isEqualTo("DIAN_ACEPTADO");
    }

    @Test
    void noReenviaUnaFacturaYaAceptadaPorDian() {
        factura.setEstadoDian("DIAN_ACEPTADO");

        service.enviarDian(id);

        verifyNoInteractions(dataico);
    }

    @Test
    void enviaSoloCorreoAlDestinatarioEditadoYActualizaElEstado() {
        when(dataico.consultarFactura("dataico", "FEE1")).thenReturn(estado("DIAN_ACEPTADO", "CORREO_ENVIADO"));

        var resultado = service.enviarCorreo(id, "  nuevo@example.test  ");

        verify(dataico).reenviar("dataico", "uuid-dataico", false, true, "nuevo@example.test");
        verify(dataico, never()).emitir(anyString(), any());
        assertThat(resultado.clienteEmail()).isEqualTo("nuevo@example.test");
        assertThat(resultado.estadoCorreo()).isEqualTo("ENVIADO");
    }

    @Test
    void conservaPendienteSiElEnvioFueRecibidoPeroLaConsultaPosteriorFalla() {
        when(dataico.consultarFactura("dataico", "FEE1")).thenThrow(new DataicoApiException("Timeout al consultar", new RuntimeException()));

        var resultado = service.enviarCorreo(id, "nuevo@example.test");

        assertThat(resultado.estadoCorreo()).isEqualTo("PENDIENTE");
        assertThat(resultado.clienteEmail()).isEqualTo("nuevo@example.test");
        verify(facturas).saveAndFlush(factura);
    }

    @Test
    void noMarcaEnviadoNiCambiaDestinatarioSiDataicoRechazaLaSolicitud() {
        doThrow(new DataicoApiException("Rechazado", new RuntimeException())).when(dataico)
                .reenviar(eq("dataico"), eq("uuid-dataico"), eq(false), eq(true), anyString());

        assertThatThrownBy(() -> service.enviarCorreo(id, "nuevo@example.test")).isInstanceOf(DataicoApiException.class);

        assertThat(factura.getClienteEmail()).isEqualTo("original@example.test");
        assertThat(factura.getEstadoCorreo()).isEqualTo("NO_ENVIADO");
        verify(facturas, never()).saveAndFlush(any());
    }

    @Test
    void impideEnviosSinUuidDeDataico() {
        factura.setIdentificadorDataico(null);
        assertThatThrownBy(() -> service.enviarDian(id)).hasMessageContaining("identificador de Dataico");
        verifyNoInteractions(dataico);
    }

    @Test
    void validaCorreosVaciosOInvalidosEnElContratoDeLaApi() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new EnviarCorreoFacturaRequest(""))).isNotEmpty();
            assertThat(validator.validate(new EnviarCorreoFacturaRequest("correo-invalido"))).isNotEmpty();
            assertThat(validator.validate(new EnviarCorreoFacturaRequest("cliente@example.test"))).isEmpty();
        }
    }

    private ResultadoEmisionDocumento estado(String dian, String correo) {
        return new ResultadoEmisionDocumento("dataico", "uuid-dataico", "FEE1", dian,
                null, null, null, "Estado actualizado", correo);
    }
}
