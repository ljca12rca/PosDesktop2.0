package com.posdesktop.pos.invoicing.domain.dian;

import com.posdesktop.pos.invoicing.domain.InvoiceValidationException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Catalogos locales de las tablas XLSX oficiales de la caja FE 1.9 v2026. */
public final class DianCatalogs {
    private final Map<String, Map<String, String>> tables;
    public DianCatalogs() {
        Map<String, Map<String, String>> values = new HashMap<>();
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/dian19/catalogs.tsv"));
             var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            reader.lines().forEach(line -> {
                String[] columns = line.split("\t", 3);
                values.computeIfAbsent(columns[0], ignored -> new HashMap<>()).put(columns[1], columns[2]);
            });
        } catch (IOException e) { throw new IllegalStateException("Catalogos DIAN ausentes.", e); }
        Map<String, Map<String, String>> frozen = new HashMap<>();
        values.forEach((key, value) -> frozen.put(key, Map.copyOf(value)));
        tables = Map.copyOf(frozen);
    }
    public String require(String table, String code) {
        String label = tables.getOrDefault(table, Map.of()).get(code);
        if (label == null) throw new InvoiceValidationException(table, "DIAN_CATALOG", "Codigo no publicado en el catalogo " + table + ": " + code);
        return label;
    }
    public Map<String, String> entries(String table) { return tables.getOrDefault(table, Map.of()); }
}
