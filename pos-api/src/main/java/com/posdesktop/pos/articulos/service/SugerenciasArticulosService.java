package com.posdesktop.pos.articulos.service;

import com.posdesktop.pos.articulos.api.dto.SugerenciaArticuloResponse;
import com.posdesktop.pos.modelo.relacional.Articulo;
import com.posdesktop.pos.repositorio.relacional.ArticuloRepositorio;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catálogo auxiliar de nombres. No administra existencias ni modifica precios del catálogo. */
@Service
public class SugerenciasArticulosService {
    private static final Set<String> NOMBRES_GENERICOS = Set.of("artículo manual", "articulo manual", "venta manual");
    private final ArticuloRepositorio articulos;

    public SugerenciasArticulosService(ArticuloRepositorio articulos) {
        this.articulos = articulos;
    }

    @Transactional(readOnly = true)
    public List<SugerenciaArticuloResponse> sugerir(String texto, BigDecimal precio) {
        String termino = normalizarNombre(texto).toLowerCase(Locale.ROOT);
        if (termino.length() > 200) throw new IllegalArgumentException("El nombre del artículo admite hasta 200 caracteres.");
        BigDecimal valor = precio == null ? null : precio.setScale(2, RoundingMode.HALF_UP);
        if (valor != null && valor.signum() < 0) throw new IllegalArgumentException("El precio no puede ser negativo.");
        if (termino.isEmpty() && valor == null) return List.of();
        return articulos.buscarSugerencias(termino, valor).stream()
                .map(articulo -> new SugerenciaArticuloResponse(articulo.getId(), articulo.getNombre(), articulo.getPrecioBase()))
                .toList();
    }

    /** Se ejecuta en la transacción de la venta: los borradores descartados no crean artículos. */
    @Transactional
    public Articulo aprender(String descripcion, BigDecimal precio) {
        String nombre = normalizarNombre(descripcion);
        String clave = nombre.toLowerCase(Locale.ROOT);
        if (clave.isEmpty() || NOMBRES_GENERICOS.contains(clave)) return null;
        if (nombre.length() > 200) throw new IllegalArgumentException("El nombre del artículo admite hasta 200 caracteres.");
        var existente = articulos.buscarPorNombreNormalizado(clave, precio);
        if (existente.isPresent()) return existente.get().isActivo() ? existente.get() : null;

        String sku = "AUTO-" + UUID.nameUUIDFromBytes(clave.getBytes(StandardCharsets.UTF_8));
        articulos.insertarSugerenciaSiNoExiste(UUID.randomUUID(), sku, nombre, precio);
        return articulos.findBySku(sku).filter(Articulo::isActivo).orElse(null);
    }

    private String normalizarNombre(String nombre) {
        return nombre == null ? "" : nombre.trim().replaceAll("\\s+", " ");
    }
}
