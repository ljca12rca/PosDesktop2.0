package com.posdesktop.pos.ventas.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.posdesktop.pos.articulos.service.SugerenciasArticulosService;
import com.posdesktop.pos.cierres.service.CierreDiarioAutoSyncService;
import com.posdesktop.pos.modelo.enumeraciones.TipoDetalleVenta;
import com.posdesktop.pos.modelo.relacional.Articulo;
import com.posdesktop.pos.modelo.relacional.Venta;
import com.posdesktop.pos.repositorio.relacional.CierreDiarioRepositorio;
import com.posdesktop.pos.repositorio.relacional.VentaRepositorio;
import com.posdesktop.pos.ventas.api.dto.RegistrarDetalleVentaRequest;
import com.posdesktop.pos.ventas.api.dto.RegistrarVentaManualRequest;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class VentasSugerenciasTest {
    private final VentaRepositorio ventas = mock(VentaRepositorio.class);
    private final SugerenciasArticulosService sugerencias = mock(SugerenciasArticulosService.class);
    private final VentasService service = new VentasService(ventas, mock(CierreDiarioRepositorio.class),
            mock(CierreDiarioAutoSyncService.class), sugerencias, "VTA");

    @Test
    void guardaDescripcionYPrecioDigitadosConReferenciaAuxiliarSinConvertirEnVentaDeInventario() {
        Articulo articulo = new Articulo();
        articulo.setNombre("Café");
        articulo.setPrecioBase(new BigDecimal("2000"));
        when(sugerencias.aprender("CAFÉ", new BigDecimal("3000.00"))).thenReturn(articulo);
        when(ventas.saveAndFlush(any())).thenAnswer(invocacion -> invocacion.getArgument(0));

        var respuesta = service.registrarVentaManual(new RegistrarVentaManualRequest(
                List.of(new RegistrarDetalleVentaRequest("CAFÉ", new BigDecimal("2"), new BigDecimal("3000"))),
                null, null, null));

        ArgumentCaptor<Venta> captor = ArgumentCaptor.forClass(Venta.class);
        verify(ventas).saveAndFlush(captor.capture());
        var detalle = captor.getValue().getDetalles().getFirst();
        assertThat(detalle.getArticulo()).isSameAs(articulo);
        assertThat(detalle.getTipoDetalle()).isEqualTo(TipoDetalleVenta.MANUAL);
        assertThat(detalle.getDescripcion()).isEqualTo("CAFÉ");
        assertThat(detalle.getPrecioUnitario()).isEqualByComparingTo("3000");
        assertThat(respuesta.total()).isEqualByComparingTo("6000");
    }

    @Test
    void losAbonosDeSeparadosNoSeAprendenComoArticulos() {
        when(ventas.saveAndFlush(any())).thenAnswer(invocacion -> invocacion.getArgument(0));
        service.registrarVentaSeparado("Abono separado SEP-001", BigDecimal.TEN, null, null);
        verifyNoInteractions(sugerencias);
    }
}
