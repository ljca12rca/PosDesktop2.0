package com.posdesktop.pos.repositorio.relacional;

import com.posdesktop.pos.modelo.relacional.Articulo;
import java.util.Optional;
import java.util.List;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ArticuloRepositorio extends JpaRepository<Articulo, UUID> {

    Optional<Articulo> findBySku(String sku);

    @Query(value = """
            SELECT * FROM articulos
            WHERE activo = TRUE AND (
                (:texto <> '' AND position(:texto in lower(regexp_replace(trim(nombre), '[[:space:]]+', ' ', 'g'))) > 0)
                OR (:texto = '' AND precio_base = CAST(:precio AS numeric)))
            ORDER BY CASE WHEN precio_base = CAST(:precio AS numeric) THEN 0 ELSE 1 END,
                CASE WHEN position(:texto in lower(regexp_replace(trim(nombre), '[[:space:]]+', ' ', 'g'))) = 1 THEN 0 ELSE 1 END,
                lower(nombre), sku
            LIMIT 12
            """, nativeQuery = true)
    List<Articulo> buscarSugerencias(@Param("texto") String texto, @Param("precio") BigDecimal precio);

    // Incluye inactivos: escribir su nombre no debe reactivarlos ni crear reemplazos.
    @Query(value = """
            SELECT * FROM articulos
            WHERE lower(regexp_replace(trim(nombre), '[[:space:]]+', ' ', 'g')) = :nombre
            ORDER BY activo DESC,
                CASE WHEN precio_base = :precio THEN 0 ELSE 1 END, sku
            LIMIT 1
            """, nativeQuery = true)
    Optional<Articulo> buscarPorNombreNormalizado(@Param("nombre") String nombre, @Param("precio") BigDecimal precio);

    // Un SKU estable y ON CONFLICT evitan duplicados si dos cajas aprenden el mismo nombre a la vez.
    @Modifying
    @Query(value = """
            INSERT INTO articulos (id, sku, nombre, precio_base, activo, creado_en, actualizado_en)
            VALUES (:id, :sku, :nombre, :precio, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT (sku) DO NOTHING
            """, nativeQuery = true)
    int insertarSugerenciaSiNoExiste(@Param("id") UUID id, @Param("sku") String sku,
            @Param("nombre") String nombre, @Param("precio") BigDecimal precio);
}
