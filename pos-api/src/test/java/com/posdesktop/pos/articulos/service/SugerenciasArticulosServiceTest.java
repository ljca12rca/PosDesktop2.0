package com.posdesktop.pos.articulos.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.posdesktop.pos.modelo.relacional.Articulo;
import com.posdesktop.pos.repositorio.relacional.ArticuloRepositorio;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SugerenciasArticulosServiceTest {
    private final ArticuloRepositorio repositorio = mock(ArticuloRepositorio.class);
    private final SugerenciasArticulosService service = new SugerenciasArticulosService(repositorio);

    @Test
    void reutilizaNombreSinModificarPrecioSkuOEstadoDelArticulo() {
        Articulo articulo = new Articulo();
        articulo.setNombre("Café");
        articulo.setSku("INVENTARIO-001");
        articulo.setPrecioBase(new BigDecimal("2000"));
        when(repositorio.buscarPorNombreNormalizado("café", new BigDecimal("3000"))).thenReturn(Optional.of(articulo));

        assertThat(service.aprender("  CAFÉ  ", new BigDecimal("3000"))).isSameAs(articulo);

        assertThat(articulo.getPrecioBase()).isEqualByComparingTo("2000");
        assertThat(articulo.getSku()).isEqualTo("INVENTARIO-001");
        verify(repositorio, never()).insertarSugerenciaSiNoExiste(any(), any(), any(), any());
        verify(repositorio, never()).save(any());
    }

    @Test
    void noReactivaArticulosInactivosNiCreaDuplicados() {
        Articulo articulo = new Articulo();
        articulo.setActivo(false);
        when(repositorio.buscarPorNombreNormalizado("café", BigDecimal.TEN)).thenReturn(Optional.of(articulo));
        assertThat(service.aprender("Café", BigDecimal.TEN)).isNull();
        assertThat(articulo.isActivo()).isFalse();
        verify(repositorio, never()).insertarSugerenciaSiNoExiste(any(), any(), any(), any());
    }

    @Test
    void noAprendeNombresGenericosNiVacios() {
        for (String nombre : List.of("", "  ", "Artículo manual", "ARTICULO MANUAL", "Venta manual")) {
            assertThat(service.aprender(nombre, BigDecimal.TEN)).isNull();
        }
        assertThat(service.aprender(null, BigDecimal.TEN)).isNull();
        verifyNoInteractions(repositorio);
    }

    @Test
    void busquedaNormalizaTextoYPrecioYAdmiteBuscarSoloPorPrecio() {
        when(repositorio.buscarSugerencias(any(), any())).thenReturn(List.of());
        service.sugerir("  CAJA   azul  ", new BigDecimal("15.125"));
        service.sugerir("", new BigDecimal("5000"));
        verify(repositorio).buscarSugerencias("caja azul", new BigDecimal("15.13"));
        verify(repositorio).buscarSugerencias("", new BigDecimal("5000.00"));
    }

    @Test
    void validaEntradasYSinFiltrosNoConsultaTodoElCatalogo() {
        assertThat(service.sugerir("", null)).isEmpty();
        assertThatThrownBy(() -> service.sugerir("a".repeat(201), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.sugerir("café", BigDecimal.ONE.negate())).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repositorio);
    }
}
